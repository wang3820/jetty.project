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

class BindingJettyMethodHandle implements JettyMethodHandle
{
    public MethodHandle _methodHandle;

    BindingJettyMethodHandle(MethodHandle methodHandle)
    {
        _methodHandle = methodHandle;
    }

    @Override
    public Object invoke(Object... args) throws Throwable
    {
        return _methodHandle.invokeWithArguments(args);
    }

    @Override
    public BindingJettyMethodHandle bindTo(Object arg)
    {
        _methodHandle = _methodHandle.bindTo(arg);
        return this;
    }

    @Override
    public JettyMethodHandle bindTo(Object arg, int idx)
    {
        _methodHandle = MethodHandles.insertArguments(_methodHandle, idx, arg);
        return this;
    }

    @Override
    public BindingJettyMethodHandle filterReturnValue(MethodHandle filter)
    {
        _methodHandle = MethodHandles.filterReturnValue(_methodHandle, filter);
        return this;
    }

    @Override
    public BindingJettyMethodHandle changeReturnType(Class<Object> objectClass)
    {
        _methodHandle = _methodHandle.asType(_methodHandle.type().changeReturnType(objectClass));
        return this;
    }

    @Override
    public Class<?> parameterType(int idx)
    {
        return _methodHandle.type().parameterType(idx);
    }

    @Override
    public Class<?> returnType()
    {
        return _methodHandle.type().returnType();
    }
}
