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

package org.netbeans.modules.payara.tooling.data;

import org.netbeans.modules.payara.tooling.PayaraIdeException;

/**
 * Payara IDE SDK Exception related to server administration command package
 * problems.
 * <p>
 * All exceptions are logging themselves on WARNING level when created.
 * <p>
 * @author Tomas Kraus, Peter Benedikovic
 */
public class DataException extends PayaraIdeException {

    ////////////////////////////////////////////////////////////////////////////
    // Class attributes                                                       //
    ////////////////////////////////////////////////////////////////////////////

    /** Exception message for invalid Payara administration interface
     *  type. */
    static final String INVALID_ADMIN_INTERFACE =
            "Invalid Payara administration interface type";

    /** Exception message for invalid Payara version. */
    static final String INVALID_CONTAINER = "Invalid Payara container";

    /** Exception message for invalid Payara URL.
     *  Used in IDE URL entity class. */
    public static final String INVALID_URL = "Invalid Payara URL";

    /** Exception for Payara installation root directory null value. */
    static final String SERVER_ROOT_NULL
            = "Payara installation root directory is null";

    /** Exception for Payara home directory null value. */
    static final String SERVER_HOME_NULL
            = "Payara home directory is null";

    /** Exception for non existent Payara installation root directory.
        Requires 1 directory argument.*/
    static final String SERVER_ROOT_NONEXISTENT
            = "Payara installation root directory {0} does not exist";

    /** Exception for non existent Payara home directory.
        Requires 1 directory argument.*/
    static final String SERVER_HOME_NONEXISTENT
            = "Payara home directory {0} does not exist";

    /** Exception for unknown Payara version in Payara home directory.
     */
    static final String SERVER_HOME_NO_VERSION
            = "Unknown Payara version in home directory {0}";

    /**  Exception for Payara URL null value. */
    static final String SERVER_URL_NULL = "Payara URL is null";

    ////////////////////////////////////////////////////////////////////////////
    // Constructors                                                           //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs an instance of <code>DataException</code> without
     * detail message.
     */
    public DataException() {
        super();
    }

    /**
     * Constructs an instance of <code>DataException</code> with the
     * specified detail message.
     * <p>
     * @param msg The detail message.
     */
    public DataException(final String msg) {
        super(msg);
    }

    /**
     * Constructs an instance of <code>DataException</code> with the
     * specified detail message and arguments.
     * <p/>
     * Uses {@link java.text.MessageFormat} to format message.
     * <p/>
     * @param msg The detail message.
     * @param arguments Arguments to be inserted into message.
     */
    public DataException(final String msg, final Object... arguments) {
        super(msg, arguments);
    }

    /**
     * Constructs an instance of <code>DataException</code> with the
     * specified detail message and cause. Exception is logged on WARN level.
     * <p>
     * Note that the detail message associated with {@code cause} is <i>not</i>
     * automatically incorporated in this runtime exception's detail message.
     * <p>
     * @param msg   the detail message (which is saved for later retrieval
     *              by the {@link #getMessage()} method).
     * @param cause the cause (which is saved for later retrieval by the
     *              {@link #getCause()} method).  (A <code>null</code> value is
     *              permitted, and indicates that the cause is nonexistent or
     *              unknown.)
     */
    public DataException(final String msg, final Throwable cause) {
        super(msg, cause);
    }

}
