/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 */
// Portions Copyright [2017] [Payara Foundation and/or its affiliates]

package org.netbeans.modules.payara.tooling.server;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import org.netbeans.modules.payara.tooling.PayaraIdeException;
import org.netbeans.modules.payara.tooling.admin.CommandStartDAS;
import org.netbeans.modules.payara.tooling.admin.ResultProcess;
import org.netbeans.modules.payara.tooling.admin.ServerAdmin;
import org.netbeans.modules.payara.tooling.data.StartupArgs;
import org.netbeans.modules.payara.tooling.logging.Logger;
import org.netbeans.modules.payara.tooling.server.parser.JvmConfigReader;
import org.netbeans.modules.payara.tooling.server.parser.TreeParser;
import org.netbeans.modules.payara.tooling.utils.JavaUtils;
import org.netbeans.modules.payara.tooling.utils.OsUtils;
import org.netbeans.modules.payara.tooling.utils.ServerUtils;
import org.netbeans.modules.payara.tooling.utils.Utils;
import org.netbeans.modules.payara.tooling.data.PayaraServer;

/**
 * This class should contain task methods for GF server.
 * <p/>
 * @author Tomas Kraus, Peter Benedikovic
 */
public class ServerTasks {

    ////////////////////////////////////////////////////////////////////////////
    // Inner classes                                                          //
    ////////////////////////////////////////////////////////////////////////////

    public enum StartMode {
        /** Regular server start. */
        START,
        /** Start server in debug mode. */
        DEBUG,
        /** Start server in profiling mode. */
        PROFILE;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Class attributes                                                       //
    ////////////////////////////////////////////////////////////////////////////

    /** Logger instance for this class. */
    private static final Logger LOGGER = new Logger(ServerTasks.class);

    /** Default name of the DAS server. */
    private static final String DAS_NAME = "server";

    /**
     * Build server variables map.
     * <p/>
     * @param server   Payara server entity
     * @param javaHome Java SE JDK home used to run Payara.
     */
    private static Map varMap(PayaraServer server, String javaHome) {
        HashMap<String, String> varMap = new HashMap<>();
        varMap.put(ServerUtils.PF_HOME_PROPERTY, server.getServerHome());
        varMap.put(ServerUtils.PF_DOMAIN_ROOT_PROPERTY,
                ServerUtils.getDomainPath(server));
        varMap.put(ServerUtils.PF_JAVA_ROOT_PROPERTY, javaHome);
        varMap.put(ServerUtils.PF_DERBY_ROOT_PROPERTY,
                ServerUtils.getDerbyRoot(server));
        return varMap;
    }

    /**
     * Add java agents into server options.
     * <p/>
     * @param server          Payara server entity.
     * @param jvmConfigReader Contains <code>jvm-options</code>
     *                        from <code>domain.xwl</code>.
     */
    private static void addJavaAgent(PayaraServer server,
            JvmConfigReader jvmConfigReader) {
        List<String> optList = jvmConfigReader.getOptList();
        File serverHome = new File(server.getServerHome());
        File btrace = new File(serverHome, "lib/monitor/btrace-agent.jar");
        File flight = new File(serverHome, "lib/monitor/flashlight-agent.jar");
        if (jvmConfigReader.isMonitoringEnabled()) {
            if (btrace.exists()) {
                optList.add("-javaagent:" + Utils.
                        quote(btrace.getAbsolutePath())
                        + "=unsafe=true,noServer=true"); // NOI18N
            } else if (flight.exists()) {
                optList.add("-javaagent:"
                        + Utils.quote(flight.getAbsolutePath()));
            }
        }
    }

    /**
     * Adds server variables from variables map into Java VM options
     * for server startup.
     * <p/>
     * @param javaOpts Java VM options {@link StringBuilder} instance.
     * @param varMap Server variables map.
     */
    private static void appendVarMap(
            StringBuilder javaOpts, Map<String, String> varMap) {
        for (Map.Entry<String, String> entry : varMap.entrySet()) {
            javaOpts.append(' ');
            JavaUtils.systemProperty(
                    javaOpts, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Starts local GF server.
     * <p/>
     * The own start is done by calling CommandStartDAS. This method prepares
     * command-line arguments that need to be provided for the command.
     * The parameters come from domain.xml and from parameter <code>args</code>
     * provided by caller.
     * <p/>
     * @param server Payara server entity.
     * @param args   Startup arguments provided by caller.
     * @param mode   Mode which we are starting GF in.
     * @return ResultProcess returned by CommandStartDAS to give caller
     *         opportunity to monitor the start process.
     * @throws PayaraIdeException
     */
    public static ResultProcess startServer(PayaraServer server,
            StartupArgs args, StartMode mode) throws PayaraIdeException {
        final String METHOD = "startServer";
        // reading jvm config section from domain.xml
        JvmConfigReader jvmConfigReader = new JvmConfigReader(DAS_NAME);
        String domainAbsolutePath = server.getDomainsFolder() + File.separator
                + server.getDomainName();
        String domainXmlPath = domainAbsolutePath + File.separator + "config"
                + File.separator + "domain.xml";
        if (!TreeParser.readXml(new File(domainXmlPath), jvmConfigReader)) {
            // retry with platform default
            LOGGER.log(Level.INFO, "Retrying with {0} encoding", Charset.defaultCharset());
            jvmConfigReader = new JvmConfigReader(DAS_NAME);
            if (!TreeParser.readXml(new File(domainXmlPath), Charset.defaultCharset(), jvmConfigReader)) {
                throw new PayaraIdeException(LOGGER.excMsg(
                        METHOD, "readXMLerror"), domainXmlPath);
            }
        }
        List<String> optList = jvmConfigReader.getOptList();
        Map<String, String> propMap = jvmConfigReader.getPropMap();
        addJavaAgent(server, jvmConfigReader);
        // try to find bootstraping jar - usually glassfish.jar
        File bootstrapJar = ServerUtils.getJarName(server.getServerHome(),
                ServerUtils.GFV3_JAR_MATCHER);
        if (bootstrapJar == null) {
            throw new PayaraIdeException(
                    LOGGER.excMsg(METHOD, "noBootstrapJar"));
        }
        // compute classpath using properties from jvm-config element of
        // domain.xml
        String classPath = computeClassPath(propMap,
                new File(domainAbsolutePath), bootstrapJar);

        StringBuilder javaOpts = new StringBuilder(1024);
        StringBuilder payaraArgs = new StringBuilder(256);
        // preparing variables to replace placeholders in options
        Map<String, String> varMap = varMap(server, args.getJavaHome());
        // Add debug parameters read from domain.xml.
        // It's important to add them before java options specified by user
        // in case user specified it by himslef.
        if (mode.equals(StartMode.DEBUG)) {
            String debugOpts = propMap.get("debug-options");
            String[] debugOptsSplited = debugOpts.split("\\s+(?=-)");
            optList.addAll(Arrays.asList(debugOptsSplited));
        }
        // add profile parameters
        if (mode.equals(StartMode.PROFILE)) {
        }
        // appending IDE specified options after the ones got from domain.xml
        // IDE specified are takind precedence this way
        if (args.getJavaArgs() != null) {
            optList.addAll(args.getJavaArgs());
        }
        appendOptions(javaOpts, optList, varMap);
        appendVarMap(javaOpts, varMap);
        if (args.getPayaraArgs() != null) {
            appendPayaraArgs(payaraArgs, args.getPayaraArgs());
        }
        // starting the server using command
        CommandStartDAS startCommand = new CommandStartDAS(args.getJavaHome(),
                classPath, javaOpts.toString(), payaraArgs.toString(),
                domainAbsolutePath);
        Future<ResultProcess> future =
                ServerAdmin.<ResultProcess>exec(server, startCommand);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new PayaraIdeException(LOGGER.excMsg(METHOD, "failed"), e);
        }
    }

    /**
     * Convenient method to start payara in START mode.
     * <p/>
     * @param server Payara server entity.
     * @param args   Startup arguments provided by caller.
     * @return ResultProcess returned by CommandStartDAS to give caller
     *         opportunity to monitor the start process.
     * @throws PayaraIdeException
     */
    public static ResultProcess startServer(PayaraServer server,
            StartupArgs args) throws PayaraIdeException {
        return startServer(server, args, StartMode.START);
    }

    /**
     * Computing class path for <code>-cp</code> option of java.
     * <p/>
     * @param propMap      Attributes of <code>jvm-config</code> element
     *                     of <code>domain.xml</code>.
     * @param domainDir    Relative paths will be added to this directory.
     * @param bootstrapJar Bootstrap jar will be also added to class path.
     * @return Class path for <code>-cp</code> option of java.
     */
    private static String computeClassPath(Map<String, String> propMap,
            File domainDir, File bootstrapJar) {
        final String METHOD = "computeClassPath";
        String result = null;
        List<File> prefixCP = Utils.classPathToFileList(
                propMap.get("classpath-prefix"), domainDir);
        List<File> suffixCP = Utils.classPathToFileList(
                propMap.get("classpath-suffix"), domainDir);
        boolean useEnvCP = "false".equals(
                propMap.get("env-classpath-ignored"));
        List<File> envCP = Utils.classPathToFileList(
                useEnvCP ? System.getenv("CLASSPATH") : null, domainDir);
        List<File> systemCP = Utils.classPathToFileList(
                propMap.get("system-classpath"), domainDir);

        if (prefixCP.size() > 0 || suffixCP.size() > 0 || envCP.size() > 0
                || systemCP.size() > 0) {
            List<File> mainCP = Utils.classPathToFileList(
                    bootstrapJar.getAbsolutePath(), null);

            if (mainCP.size() > 0) {
                List<File> completeCP = new ArrayList<>(32);
                completeCP.addAll(prefixCP);
                completeCP.addAll(mainCP);
                completeCP.addAll(systemCP);
                completeCP.addAll(envCP);
                completeCP.addAll(suffixCP);

                // Build classpath in proper order - prefix / main / system 
                // / environment / suffix
                // Note that completeCP should always have at least 2 elements
                // at this point (1 from mainCP and 1 from some other CP
                // modifier)
                StringBuilder classPath = new StringBuilder(1024);
                Iterator<File> iter = completeCP.iterator();
                classPath.append(Utils.quote(iter.next().getPath()));
                while (iter.hasNext()) {
                    classPath.append(File.pathSeparatorChar);
                    classPath.append(Utils.quote(iter.next().getPath()));
                }
                result = classPath.toString();
            } else {
                LOGGER.log(Level.WARNING, METHOD, "cpError");
            }
        }
        return result;
    }

    /**
     * Takes an list of java options and produces a valid string that can be put
     * on command line.
     * <p/>
     * There are two kinds of options that can be found in option list:
     * <code>key=value</code> and simple options not containing
     * <code>=</code>.
     * In the list there are both options from domain.xml and users options.
     * Thus some of them can be there more than once. For <code>key=value</code>
     * ones we can detect it and only the latest one in list will be appended to
     * command-line. For simple once maybe some duplicate detection will be
     * added in the future.
     * <p/>
     * @param argumentBuf Returned string.
     * @param optList     List of java options.
     * @param varMap      Map to be used for replacing place holders, Contains
     *                    <i>place holder</i> - <i>place holder</i> value pairs.
     */
    private static void appendOptions(StringBuilder argumentBuf,
            List<String> optList, Map<String, String> varMap) {
        final String METHOD = "appendOptions";
        HashMap<String, String> keyValueArgs = new HashMap<>();
        LinkedList<String> keyOrder = new LinkedList<>();
        String name, value;
        // first process optList aquired from domain.xml 
        for (String opt : optList) {
            // do placeholder substitution
            opt = Utils.doSub(opt.trim(), varMap);
            int splitIndex = opt.indexOf('=');
            // && !opt.startsWith("-agentpath:") is a temporary hack to
            // not touch already quoted -agentpath. Later we should handle it
            // in a better way.
            if (splitIndex != -1 && !opt.startsWith("-agentpath:")) {
                // key=value type of option
                name = opt.substring(0, splitIndex);
                value = Utils.quote(opt.substring(splitIndex + 1));
                LOGGER.log(Level.FINER, METHOD,
                        "jvmOptVal", new Object[] {name, value});

            } else {
                name = opt;
                value = null;
                LOGGER.log(Level.FINER, METHOD, "jvmOpt", name);
            }
            if (!keyValueArgs.containsKey(name)) {
                keyOrder.add(name);
            }
            keyValueArgs.put(name, value);
        }

        // override the values that are found in the domain.xml file.
        // this is totally a copy/paste from StartTomcat...
        final String[] PROXY_PROPS = {
            "http.proxyHost", // NOI18N
            "http.proxyPort", // NOI18N
            "http.nonProxyHosts", // NOI18N
            "https.proxyHost", // NOI18N
            "https.proxyPort", // NOI18N
        };
        boolean isWindows = OsUtils.isWin();
        for (String prop : PROXY_PROPS) {
            value = System.getProperty(prop);
            if (value != null && value.trim().length() > 0) {
                if (isWindows && "http.nonProxyHosts".equals(prop)) {
                    // enclose in double quotes to escape the pipes separating
                    // the hosts on windows
                    value = "\"" + value + "\""; // NOI18N
                }
                keyValueArgs.put(JavaUtils.systemPropertyName(prop), value);
            }
        }

        // appending key=value options to the command line argument
        // using the same order as they came in argument - important!
        for (String key : keyOrder) {
            argumentBuf.append(' ');
            argumentBuf.append(key);
            if (keyValueArgs.get(key) != null) {
                argumentBuf.append("="); // NOI18N
                argumentBuf.append(keyValueArgs.get(key));
            }
        }
    }

    /**
     * Append Payara startup arguments to given {@link StringBuilder}.
     * <p/>
     * @param payaraArgs     Target {@link StringBuilder} to append arguments.
     * @param payaraArgsList Arguments to be appended.
     */
    private static void appendPayaraArgs(StringBuilder payaraArgs,
            List<String> payaraArgsList) {
        for (String arg : payaraArgsList) {
            payaraArgs.append(' ');
            payaraArgs.append(arg);
        }
        // remove the first space
        if (payaraArgs.length() > 0) {
            payaraArgs.deleteCharAt(0);
        }
    }
}