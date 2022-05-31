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
import java.lang.invoke.WrongMethodTypeException;
import java.util.Objects;

class NonBindingJettyMethodHandle implements JettyMethodHandle
{
    private final MethodHandle _methodHandle;
    private final Object[] _parameters;
    private final Boolean[] _boundParams;
    private final int _numParams;
    private Class<Object> _returnType;
    private MethodHandle _returnFilter;

    public NonBindingJettyMethodHandle(MethodHandle methodHandle)
    {
        _methodHandle = Objects.requireNonNull(methodHandle);
        _numParams = methodHandle.type().parameterCount();
        _parameters = new Object[_numParams];
        _boundParams = new Boolean[_numParams];
    }

    private int getInternalIndex(int idx)
    {
        int index = -1;
        for (int i = 0; i < _numParams; i++)
        {
            if (!Boolean.TRUE.equals(_boundParams[i]))
            {
                index += 1;
                if (index == idx)
                    return i;
            }
        }

        throw new IndexOutOfBoundsException(idx);
    }

    @Override
    public Object invoke(Object... args) throws Throwable
    {
        insertArguments(false, 0, args);
        Object o = _methodHandle.invokeWithArguments(_parameters);
        if (_returnFilter != null)
            o = _returnFilter.invoke(o);
        return (_returnType == null) ? o : _returnType.cast(o);
    }

    @Override
    public JettyMethodHandle bindTo(Object arg)
    {
        int internalIndex = getInternalIndex(0);
        _parameters[internalIndex] = arg;
        _boundParams[internalIndex] = true;
        return this;
    }

    @Override
    public JettyMethodHandle insertArguments(int idx, Object... vals)
    {
        return insertArguments(true, idx, vals);
    }

    private JettyMethodHandle insertArguments(boolean bind, int idx, Object... args)
    {
        int index = 0;
        int argsIndex = 0;
        for (int i = 0; i < _numParams; i++)
        {
            if (!Boolean.TRUE.equals(_boundParams[i]))
            {
                if (index >= idx && argsIndex < args.length)
                {
                    Object val = args[argsIndex++];
                    _parameters[i] = val;
                    _boundParams[i] = bind;
                }
                else
                {
                    _parameters[i] = null;
                }
                index++;
            }
        }

        if (argsIndex < args.length)
            throw new WrongMethodTypeException(String.format("Expected %s params but had %s", args.length, argsIndex + 1));
        return this;
    }

    @Override
    public JettyMethodHandle filterReturnValue(MethodHandle filter)
    {
        _returnFilter = filter;
        return this;
    }

    @Override
    public JettyMethodHandle changeReturnType(Class<Object> objectClass)
    {
        _returnType = objectClass;
        return this;
    }

    @Override
    public Class<?> parameterType(int idx)
    {
        return _methodHandle.type().parameterType(getInternalIndex(idx));
    }

    @Override
    public Class<?> returnType()
    {
        return _returnType;
    }
}
