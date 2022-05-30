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
import java.lang.invoke.MethodHandles;

public class JettyMethodHandle
{
    public static JettyMethodHandle from(MethodHandle methodHandle)
    {
        if (methodHandle == null)
            return null;
        return new JettyMethodHandle(methodHandle);
    }

    public MethodHandle _methodHandle;

    public JettyMethodHandle(MethodHandle methodHandle)
    {
        _methodHandle = methodHandle;
    }

    public static JettyMethodHandle filterReturnValue(JettyMethodHandle target, MethodHandle filter)
    {
        target._methodHandle = MethodHandles.filterReturnValue(target._methodHandle, filter);
        return target;
    }

    public static JettyMethodHandle insertArguments(JettyMethodHandle retHandle, int idx, Object... vals)
    {
        retHandle._methodHandle = MethodHandles.insertArguments(retHandle._methodHandle, idx, vals);
        return retHandle;
    }

    public JettyMethodHandle changeReturnType(Class<Object> objectClass)
    {
        _methodHandle = _methodHandle.asType(_methodHandle.type().changeReturnType(objectClass));
        return this;
    }

    public Object invoke(Object... args) throws Throwable
    {
        return _methodHandle.invokeWithArguments(args);
    }

    public JettyMethodHandle bindTo(Object arg)
    {
        _methodHandle = _methodHandle.bindTo(arg);
        return this;
    }

    public Class<?> parameterType(int idx)
    {
        return _methodHandle.type().parameterType(idx);
    }

    public Class<?> returnType()
    {
        return _methodHandle.type().returnType();
    }

    public MethodHandle getWrappedMethodHandle()
    {
        return _methodHandle;
    }
}
