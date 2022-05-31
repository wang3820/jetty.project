//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.core.internal.util;

import java.lang.invoke.MethodHandle;

/**
 * Jetty interface for managing {@link MethodHandle}s.
 *
 * This differs from {@link MethodHandle}s that any calls the methods such as {@link #bindTo(Object)}
 * will change the instance of {@link JettyMethodHandle} it is called on.
 */
public interface JettyMethodHandle
{
    static JettyMethodHandle from(MethodHandle methodHandle)
    {
        if (methodHandle == null)
            return null;
        return new NonBindingJettyMethodHandle(methodHandle);
    }

    Object invoke(Object... args) throws Throwable;

    JettyMethodHandle bindTo(Object arg);

    JettyMethodHandle insertArguments(int idx, Object... vals);

    JettyMethodHandle filterReturnValue(MethodHandle filter);

    JettyMethodHandle changeReturnType(Class<Object> objectClass);

    Class<?> parameterType(int idx);

    Class<?> returnType();
}
