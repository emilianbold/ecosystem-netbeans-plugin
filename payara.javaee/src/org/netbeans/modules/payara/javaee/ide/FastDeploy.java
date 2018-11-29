/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2013 Oracle and/or its affiliates. All rights reserved.
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
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2008 Sun
 * Microsystems, Inc. All Rights Reserved.
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
 */
// Portions Copyright [2017-2018] [Payara Foundation and/or its affiliates]

package org.netbeans.modules.payara.javaee.ide;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.enterprise.deploy.shared.CommandType;
import javax.enterprise.deploy.spi.Target;
import javax.enterprise.deploy.spi.TargetModuleID;
import javax.enterprise.deploy.spi.status.ProgressEvent;
import javax.enterprise.deploy.spi.status.ProgressListener;
import javax.enterprise.deploy.spi.status.ProgressObject;
import org.netbeans.modules.payara.tooling.TaskEvent;
import org.netbeans.modules.payara.tooling.TaskState;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.payara.eecommon.api.HttpMonitorHelper;
import org.netbeans.modules.payara.eecommon.api.Utils;
import org.netbeans.modules.payara.eecommon.api.XmlFileCreator;
import org.netbeans.modules.payara.javaee.Hk2DeploymentManager;
import org.netbeans.modules.payara.javaee.ModuleConfigurationImpl;
import org.netbeans.modules.payara.javaee.ResourceRegistrationHelper;
import org.netbeans.modules.payara.spi.PayaraModule;
import org.netbeans.modules.payara.spi.PayaraModule2;
import org.netbeans.modules.j2ee.deployment.common.api.ConfigurationException;
import org.netbeans.modules.j2ee.deployment.devmodules.api.J2eeApplication;
import org.netbeans.modules.j2ee.deployment.devmodules.api.J2eeModule;
import org.netbeans.modules.j2ee.deployment.plugins.api.AppChangeDescriptor;
import org.netbeans.modules.j2ee.deployment.plugins.api.DeploymentChangeDescriptor;
import org.netbeans.modules.j2ee.deployment.plugins.spi.DeploymentContext;
import org.netbeans.modules.j2ee.deployment.plugins.spi.IncrementalDeployment;
import org.netbeans.modules.j2ee.deployment.plugins.spi.IncrementalDeployment2;
import org.netbeans.modules.j2ee.deployment.plugins.spi.config.ModuleConfiguration;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.xml.sax.SAXException;

/**
 *
 * @author Ludovic Champenois
 * @author Peter Williams
 */
public class FastDeploy extends IncrementalDeployment implements IncrementalDeployment2 {
    
    private static volatile long lastDeployHack = System.currentTimeMillis();

    private static final String GFDEPLOY = "gfdeploy"; // NOI18N
    
    private Hk2DeploymentManager dm;
    
    /** 
     * Creates a new instance of FastDeploy 
     * 
     * @param dm The deployment manager for the server instance this object
     *   deploys to.
     */
    public FastDeploy(Hk2DeploymentManager dm) {
        this.dm = dm;
    }
    
    /**
     * 
     * @param target 
     * @param app 
     * @param configuration 
     * @param file 
     * @return 
     */
    @Override
    public ProgressObject initialDeploy(Target target, final J2eeModule module, ModuleConfiguration configuration, final File dir) {
        return initialDeploy(target, module, dir, new File[0]);
    }

    @Override
    public ProgressObject initialDeploy(Target target, DeploymentContext context) {
        return initialDeploy(target, context.getModule(), context.getModuleFile(), context.getRequiredLibraries());
    }


    /**
     * Get web application context root from module configuration.
     * @param module Java EE module containing context root (WAR archive).
     * @param dir    Web application root.
     * @return 
     */
    private String getContextRoot(final J2eeModule module, final File dir) {
        ModuleConfigurationImpl mci = ModuleConfigurationImpl.get(module);
        if (null != mci) {
            try {
                return mci.getContextRoot();
            } catch (ConfigurationException ex) {
                Logger.getLogger("glassfish").log(Level.WARNING,
                        "could not getContextRoot() for {0}", dir);
                return null;
            }
        } else {
            return null;
        }
    }

    private ProgressObject initialDeploy(Target target, J2eeModule module, final File dir, final File[] requiredLibraries) {
        final PayaraModule commonSupport = dm.getCommonServerSupport();
        String url = commonSupport.getInstanceProperties().get(PayaraModule.URL_ATTR);
        String targ = Hk2DeploymentManager.getTargetFromUri(url);
        String nameSuffix = ""; // NOI18N
        if (null != targ)
            nameSuffix = "_"+targ;  // NOI18N
        final String moduleName = org.netbeans.modules.payara.spi.Utils.sanitizeName(Utils.computeModuleID(module, dir, Integer.toString(hashCode()))) +
                nameSuffix;
        String contextRoot = null;
        J2eeModule.Type type = module.getType();
        if (type == J2eeModule.Type.WAR) {
            contextRoot = getContextRoot(module, dir);
            // drop a glassfish-web.xml file in here.. if necessary
            FileObject rootOfWebApp = FileUtil.toFileObject(FileUtil.normalizeFile(dir));
            if (null != rootOfWebApp) {
                String fileName = null;
                String SUNWEB = "WEB-INF/sun-web.xml";  // NOI18N
                if (url.contains("pfv3ee6wc")) { // NOI18N
                    String GFWEB = "WEB-INF/glassfish-web.xml"; // NOI18N
                    String PYWEB = "WEB-INF/payara-web.xml"; // NOI18N
                    if (null != rootOfWebApp.getFileObject(GFWEB)) {
                        fileName = PYWEB;
                    } else if (null == rootOfWebApp.getFileObject(GFWEB)
                            && null == rootOfWebApp.getFileObject(SUNWEB)) {
                        fileName = GFWEB;
                    }
                } else {
                    if (null == rootOfWebApp.getFileObject(SUNWEB)) {
                        // add sun-web to deployed app
                        fileName = SUNWEB;
                    }
                }
                if (null != fileName) {
                    File ddFile = new File(dir, fileName);
                    addDescriptorToDeployedDirectory(module, ddFile);
                }
            }
        // Context root is in encapsulated WAR
        } else if (type == J2eeModule.Type.EAR) {
            if (module instanceof J2eeApplication) {
                for (J2eeModule child : ((J2eeApplication)module).getModules()) {
                    if (child.getType() == J2eeModule.Type.WAR) {
                        contextRoot = getContextRoot(child, dir);
                        break;
                    }
                }
            }
        }
        // XXX fix cast -- need error instance for ProgressObject to return errors
        Hk2TargetModuleID moduleId = Hk2TargetModuleID.get((Hk2Target) target,
                moduleName, contextRoot, dir.getAbsolutePath());

        // prevent issues by protecting against triggering
        //   http://java.net/jira/browse/GLASSFISH-15690
        ProgressObject po = checkAgainstGF15690(dir,moduleId);
        if (null != po) {
            return po;
        }
        final MonitorProgressObject deployProgress = new MonitorProgressObject(dm, moduleId);
        final MonitorProgressObject updateCRProgress = new MonitorProgressObject(dm, moduleId);
        deployProgress.addProgressListener(new UpdateContextRoot(updateCRProgress,moduleId, dm.getServerInstance(), J2eeModule.Type.WAR.equals(module.getType())));
        MonitorProgressObject restartProgress = new MonitorProgressObject(dm, moduleId);

        final PayaraModule2 commonSupport2 = (commonSupport instanceof PayaraModule2 ?
            (PayaraModule2)commonSupport : null);
        boolean restart = false;
        try {
            restart = HttpMonitorHelper.synchronizeMonitor(commonSupport.getInstanceProperties().get(PayaraModule.DOMAINS_FOLDER_ATTR),
                    commonSupport.getInstanceProperties().get(PayaraModule.DOMAIN_NAME_ATTR),
                    Boolean.parseBoolean(commonSupport.getInstanceProperties().get(PayaraModule.HTTP_MONITOR_FLAG)),
                    "modules/org-netbeans-modules-schema2beans.jar");
        } catch (IOException ex) {
            Logger.getLogger("payara-javaee").log(Level.WARNING, "http monitor state", ex);
        } catch (SAXException ex) {
            Logger.getLogger("payara-javaee").log(Level.WARNING, "http monitor state", ex);
        }
        ResourceRegistrationHelper.deployResources(dir,dm);
        if (restart) {
            final String cr = contextRoot;
            restartProgress.addProgressListener(new ProgressListener() {
                @Override
                public void handleProgressEvent(ProgressEvent event) {
                    if (event.getDeploymentStatus().isCompleted()) {
                        if (commonSupport2 != null && requiredLibraries.length > 0) {
                            commonSupport2.deploy(deployProgress, dir, moduleName, cr, Collections.<String, String>emptyMap(), requiredLibraries);
                        } else {
                            commonSupport.deploy(deployProgress, dir, moduleName, cr);
                        }
                    } else {
                        deployProgress.fireHandleProgressEvent(event.getDeploymentStatus());
                    }
                }
            });
            commonSupport.restartServer(restartProgress);
            return updateCRProgress;
        } else {
            if (commonSupport2 != null && requiredLibraries.length > 0) {
                commonSupport2.deploy(deployProgress, dir, moduleName, contextRoot, Collections.<String, String>emptyMap(), requiredLibraries);
            } else {
                commonSupport.deploy(deployProgress, dir, moduleName, contextRoot);
            }
            return updateCRProgress;
        }
    }

    private void addDescriptorToDeployedDirectory(J2eeModule module, File sunDDFile) {
        FileObject sunDDTemplate = Utils.getSunDDFromProjectsModuleVersion(module, sunDDFile.getName()); //FileUtil.getConfigFile(resource);
        if (sunDDTemplate != null) {
            try {
                FileObject configFolder = FileUtil.createFolder(sunDDFile.getParentFile());
                FileSystem fs = configFolder.getFileSystem();
                XmlFileCreator creator = new XmlFileCreator(sunDDTemplate, configFolder, sunDDTemplate.getName(), sunDDTemplate.getExt());
                fs.runAtomicAction(creator);
            } catch (IOException ioe) {
                Logger.getLogger("glassfish").log(Level.WARNING, "could not create {0}", sunDDTemplate.getPath());
            }
        }
    }

    private static Pattern badName = Pattern.compile(".*\\s.*_[jwrc]ar"); // NOI18N
    private static Pattern badPath = Pattern.compile(".*[\\\\/].*\\s.*_[jwrc]ar[\\\\/].*"); // NOI18N

    private ProgressObject checkAgainstGF15690(final File dir, Hk2TargetModuleID moduleId) {
        File parent = dir.getParentFile();
        if (null != parent) {
            if (GFDEPLOY.equals(parent.getName())) {
                File modules[] = dir.listFiles();
                for (File f : modules) {
                    if (f.isDirectory()) {
                        String fname = f.getName();
                        if (badName.matcher(fname).matches()) {
                            MonitorProgressObject po = new MonitorProgressObject(dm, moduleId);
                            po.operationStateChanged(TaskState.FAILED, TaskEvent.CMD_FAILED,
                                    NbBundle.getMessage(FastDeploy.class, "ERR_SPACE_IN_JAR_NAMES", fname)); // NOI18N
                            return po;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 
     * @param targetModuleID 
     * @param appChangeDescriptor 
     * @return 
     */
    @Override
    public ProgressObject incrementalDeploy(final TargetModuleID targetModuleID, AppChangeDescriptor appChangeDescriptor) {
        return incrementalDeploy(targetModuleID, appChangeDescriptor, new File[0]);
    }

    @Override
    public ProgressObject incrementalDeploy(final TargetModuleID targetModuleID, DeploymentContext context) {
        return incrementalDeploy(targetModuleID, context.getChanges(), context.getRequiredLibraries());
    }

    private ProgressObject incrementalDeploy(final TargetModuleID targetModuleID, AppChangeDescriptor appChangeDescriptor, final File[] requiredLibraries) {
        final MonitorProgressObject progressObject = new MonitorProgressObject(dm,
                (Hk2TargetModuleID) targetModuleID, CommandType.REDEPLOY);
        // prevent issues by protecting against triggering
        //   http://java.net/jira/browse/GLASSFISH-15690
        for (File f : appChangeDescriptor.getChangedFiles()) {
            String fname = f.getAbsolutePath();
            if (badPath.matcher(fname).matches()) { // NOI18N
                progressObject.operationStateChanged(TaskState.FAILED, TaskEvent.CMD_FAILED,
                        NbBundle.getMessage(FastDeploy.class, "ERR_SPACE_IN_JAR_NAMES", fname)); // NOI18N
                return progressObject;
            }
        }
        MonitorProgressObject restartObject = new MonitorProgressObject(dm, (Hk2TargetModuleID) targetModuleID,
                CommandType.REDEPLOY);
        final MonitorProgressObject updateCRObject = new MonitorProgressObject(dm,
                (Hk2TargetModuleID) targetModuleID, CommandType.REDEPLOY);
        progressObject.addProgressListener(new UpdateContextRoot(updateCRObject,(Hk2TargetModuleID) targetModuleID, dm.getServerInstance(), ! (null == targetModuleID.getWebURL())));
        final PayaraModule commonSupport = dm.getCommonServerSupport();
        final PayaraModule2 commonSupport2 = (commonSupport instanceof PayaraModule2 ?
            (PayaraModule2)commonSupport : null);
        boolean restart = false;
        try {
            restart = HttpMonitorHelper.synchronizeMonitor(
                    commonSupport.getInstanceProperties().get(PayaraModule.DOMAINS_FOLDER_ATTR),
                    commonSupport.getInstanceProperties().get(PayaraModule.DOMAIN_NAME_ATTR),
                    Boolean.parseBoolean(commonSupport.getInstanceProperties().get(PayaraModule.HTTP_MONITOR_FLAG)),
                    "modules/org-netbeans-modules-schema2beans.jar");
        } catch (IOException ex) {
            Logger.getLogger("payara-javaee").log(Level.WARNING,"http monitor state",
                    ex);
        } catch (SAXException ex) {
            Logger.getLogger("payara-javaee").log(Level.WARNING,"http monitor state",
                    ex);
        }
        long currentTime = System.currentTimeMillis();
        long sinceLast = currentTime - lastDeployHack;
        final boolean resourcesChanged = containsFileWithName("glassfish-resources.xml",appChangeDescriptor.getChangedFiles()); // NOI18N
        final boolean hasChanges = appChangeDescriptor.classesChanged() ||
                appChangeDescriptor.descriptorChanged() ||
                appChangeDescriptor.ejbsChanged() ||
                appChangeDescriptor.manifestChanged() ||
                appChangeDescriptor.serverDescriptorChanged() ||
                resourcesChanged ||
                // 
                // this accounts for a feature/bug of Payara.
                //  if a static resource is modified there is a window where later
                //  modifications might be missed... so old content gets served...
                // even when new content is available to be served.
                // That window is about 5 seconds.
                //
                // If the user is doing pathological things like adding a character 
                // and hitting save, the auto-refresh will show the right thing.
                //
                sinceLast < 5000;

        lastDeployHack = currentTime;

        if(appChangeDescriptor instanceof DeploymentChangeDescriptor) {
            DeploymentChangeDescriptor dcd = (DeploymentChangeDescriptor)appChangeDescriptor;
            if (dcd.serverResourcesChanged()) {
                File dir = getDirectoryForModule(targetModuleID);
                if (null != dir) {
                    ResourceRegistrationHelper.deployResources(dir, dm);
                }
            }
        }
                
        if (restart) {
            restartObject.addProgressListener(new ProgressListener() {

                @Override
                public void handleProgressEvent(ProgressEvent event) {
                    if (event.getDeploymentStatus().isCompleted()) {
                        if (hasChanges) {
                            if (commonSupport2 != null && requiredLibraries.length > 0) {
                                commonSupport2.redeploy(progressObject, targetModuleID.getModuleID(), null, requiredLibraries,resourcesChanged);
                            } else {
                                commonSupport.redeploy(progressObject, targetModuleID.getModuleID(),resourcesChanged);
                            }
                        } else {
                            progressObject.fireHandleProgressEvent(event.getDeploymentStatus());
                        }
                    } else {
                        progressObject.fireHandleProgressEvent(event.getDeploymentStatus());
                    }
                }
            });
            commonSupport.restartServer(restartObject);
            return updateCRObject;
        } else {
            if (hasChanges) {
                if (commonSupport2 != null && requiredLibraries.length > 0) {
                    commonSupport2.redeploy(progressObject, targetModuleID.getModuleID(), null, requiredLibraries, resourcesChanged);
                } else {
                    commonSupport.redeploy(progressObject, targetModuleID.getModuleID(), resourcesChanged);
                }
            } else {
                progressObject.operationStateChanged(TaskState.COMPLETED, TaskEvent.CMD_COMPLETED,
                        NbBundle.getMessage(FastDeploy.class, "MSG_RedeployUnneeded"));
            }
            return updateCRObject;
        }
    }
    
    /**
     * 
     * @param target 
     * @param deployable 
     * @return 
     */
    @Override
    public boolean canFileDeploy(Target target, J2eeModule deployable) {
        if (null == deployable){
            return false;
        }
        
        if (deployable.getType() == J2eeModule.Type.CAR) {
            return false;
        }

        final PayaraModule commonSupport = dm.getCommonServerSupport();
        String url = commonSupport.getInstanceProperties().get(PayaraModule.URL_ATTR);

        if (!url.trim().matches(".*:[0-9]+$"))  // NOI18N
            return url.trim().endsWith("server");

        return true;
    }
    
    /**
     * @return Absolute path root directory for the specified app or null if
     *   server can accept the deployment from an arbitrary directory.
     */
    @Override
    public File getDirectoryForNewApplication(Target target, J2eeModule app, ModuleConfiguration configuration) {
        File dest = null;
        if (app.getType() == J2eeModule.Type.EAR) {
            File tmp = getProjectDir(app);
            if (null == tmp) {
               return dest;
            }
            dest = new File(tmp, "target");  // NOI18N
            if (!dest.exists()) {
                // the app wasn't a maven project
                dest = new File(tmp, "dist");  // NOI18N
            }
            if (dest.isFile() || (dest.isDirectory() && !dest.canWrite())) {
               throw new IllegalStateException();
            }
            String moduleName = org.netbeans.modules.payara.spi.Utils.sanitizeName(Utils.computeModuleID(app, null, null));
            String dirName = GFDEPLOY;
            if (null != moduleName) {
                dirName += "/"+moduleName; // NOI18N
            }
            dest = new File(dest, dirName);
            boolean retval = true;
            if (!dest.exists()) {
                retval = dest.mkdirs();
            }
            if (!retval || !dest.isDirectory()) {
               dest = null;
            }
        }
        return dest;
    }
    
    /**
     * 
     * @param file 
     * @param string 
     * @param app 
     * @param configuration 
     * @return 
     */
    @Override
    public File getDirectoryForNewModule(File file, String string, J2eeModule app, ModuleConfiguration configuration) {
        return new File(file, transform(removeLeadSlash(string)));
    }

    private String removeLeadSlash(String s) {
        if (null == s) {
            return s;
        }
        if (s.length() < 1) {
            return s;
        }
        if (!s.startsWith("/")) {
            return s;
        }
        return s.substring(1);
    }

    static String transform(String s) {
        int len = s.length();
        if (len > 4) {
            StringBuilder sb = new StringBuilder(s);
            char tmp = sb.charAt(len - 4);
            if (tmp == '.') {
                sb.setCharAt(len-4, '_');
                return sb.toString();
            }
        }
        return s;
    }
    
    /**
     * 
     * @param targetModuleID 
     * @return 
     */
    @Override
    public File getDirectoryForModule(TargetModuleID targetModuleID) {
        File retVal
                = new File(((Hk2TargetModuleID) targetModuleID).getLocation());
        if (retVal.getPath().contains("${")) {
            throw new IllegalStateException(NbBundle.getMessage(FastDeploy.class,
                    "ERR_UndeployAndRedeploy"));
        }
        return retVal;
    }

    @Override
    public ProgressObject deployOnSave(TargetModuleID module, DeploymentChangeDescriptor desc) {
        return incrementalDeploy(module, desc);
    }

    @Override
    public boolean isDeployOnSaveSupported() {
        final PayaraModule commonSupport = dm.getCommonServerSupport();
        String url = commonSupport.getInstanceProperties().get(PayaraModule.URL_ATTR);

        if (!url.trim().matches(".*:[0-9]+$")) // NOI18N
            return url.trim().endsWith("server");
        return !"false".equals(System.getProperty("glassfish.javaee.deployonsave"));
    }

    // try to get the Project Directory as a File
    // use a couple different stratgies, since the resource.dir is in a user-
    // editable file -- but it is quicker to access, if it is there....
    //
    private File getProjectDir(J2eeModule app) {
        try {
            FileObject fo = app.getContentDirectory();
            Project p = FileOwnerQuery.getOwner(fo);
            if (null != p) {
                fo = p.getProjectDirectory();
                return FileUtil.toFile(fo);
            }
        } catch (IOException ex) {
            Logger.getLogger("payara-javaee").log(Level.FINER,    // NOI18N
                    null,ex);
        }
        java.io.File tmp = app.getResourceDirectory();

        if (tmp != null) {
            return tmp.getParentFile();
        }
        return null;
    }

    @Override
    public String getModuleUrl(TargetModuleID module) {
        assert null != module;
//        if (null == module) {
//            return "/bogusModule";
//        }
        Hk2TargetModuleID self = (Hk2TargetModuleID) module;
        String retVal = self.getModuleID();
        return retVal.startsWith("/") ? retVal : "/"+retVal;
    }

    private boolean containsFileWithName(String name, File[] changedFiles) {
        if (null == changedFiles || null == name)
            return false;
        for (File f : changedFiles) {
            String fp = null != f ? f.getAbsolutePath() : null;
            if (null != fp && fp.contains(name))
                return true;
        }
        return false;
    }
}
