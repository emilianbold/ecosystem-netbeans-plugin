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

package org.netbeans.modules.payara.tooling;

import org.netbeans.modules.payara.tooling.data.PayaraServer;
import org.netbeans.modules.payara.tooling.data.PayaraStatusTask;

/**
 * Payara server status listener.
 * <p/>
 * Receives notifications about every Payara server status check result
 * or about Payara server status changes.
 * <p/>
 * @author Tomas Kraus
 */
public interface PayaraStatusListener {

    /**
     * Callback to notify about current server status after every check
     * when enabled.
     * <p/>
     * @param server Payara server instance being monitored.
     * @param status Current server status.
     * @param task   Last Payara server status check task details.
     */
    public void currentState(final PayaraServer server,
            final PayaraStatus status, final PayaraStatusTask task);

    /**
     * Callback to notify about server status change when enabled.
     * <p/>
     * @param server Payara server instance being monitored.
     * @param status Current server status.
     * @param task   Last Payara server status check task details.
     */    
    public void newState(final PayaraServer server,
            final PayaraStatus status, final PayaraStatusTask task);

    /**
     * Callback to notify about server status check failures.
     * <p/>
     * @param server Payara server instance being monitored.
     * @param task   Payara server status check task details.
     */
    public void error(final PayaraServer server,
            final PayaraStatusTask task);

    /**
     * Callback to notify about status listener being registered.
     * <p/>
     * May be called multiple times for individual event sets during
     * registration phase.
     */
    public void added();

    /**
     * Callback to notify about status listener being unregistered.
     * <p/>
     * Will be called once during listener removal phase when was found
     * registered for at least one event set.
     */
    public void removed();

}
