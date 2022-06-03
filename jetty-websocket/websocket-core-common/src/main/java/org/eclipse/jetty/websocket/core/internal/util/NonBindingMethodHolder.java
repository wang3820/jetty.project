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

/**
 * This implementation of {@link MethodHolder} is not thread safe.
 * Mutual exclusion should be used when calling {@link #invoke(Object...)}.
 */
class NonBindingMethodHolder implements MethodHolder
{
    private final MethodHandle _methodHandle;
    private final Object[] _parameters;
    private final Boolean[] _boundParams;
    private final int _numParams;
    private Class<Object> _returnType;
    private MethodHandle _returnFilter;

    public NonBindingMethodHolder(MethodHandle methodHandle)
    {
        _methodHandle = Objects.requireNonNull(methodHandle);
        _numParams = methodHandle.type().parameterCount();
        _parameters = new Object[_numParams];
        _boundParams = new Boolean[_numParams];
    }

    private int getInternalIndex(int idx)
    {
        int index = 0;
        for (int i = 0; i < _numParams; i++)
        {
            if (_boundParams[i] != Boolean.TRUE)
            {
                if (index == idx)
                    return i;
                index++;
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
    public MethodHolder bindTo(Object arg, int idx)
    {
        int internalIndex = getInternalIndex(idx);
        _parameters[internalIndex] = arg;
        _boundParams[internalIndex] = true;
        return this;
    }

    @Override
    public MethodHolder bindTo(Object arg)
    {
        return bindTo(arg, 0);
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    private MethodHolder insertArguments(boolean bind, int idx, Object... args)
    {
        int index = 0;
        int argsIndex = 0;
        for (int i = 0; i < _numParams; i++)
        {
            if (_boundParams[i] != Boolean.TRUE)
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
        if (index != argsIndex)
            throw new WrongMethodTypeException(String.format("Expected %s params but had %s", index + 1, argsIndex + 1));
        return this;
    }

    @Override
    public MethodHolder filterReturnValue(MethodHandle filter)
    {
        _returnFilter = filter;
        return this;
    }

    @Override
    public MethodHolder changeReturnType(Class<Object> objectClass)
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
