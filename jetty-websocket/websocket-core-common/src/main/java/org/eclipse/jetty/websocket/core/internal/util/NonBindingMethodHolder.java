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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * This implementation of {@link MethodHolder} is not thread safe.
 * Mutual exclusion should be used when calling {@link #invoke(Object...)}.
 */
class NonBindingMethodHolder implements MethodHolder
{
    private final MethodHandle _methodHandle;
    private final Object[] _parameters;
    private final List<Integer> _lookupTable = new LinkedList<>();
    private List<MethodHandle> _returnFilters;

    public NonBindingMethodHolder(MethodHandle methodHandle)
    {
        _methodHandle = Objects.requireNonNull(methodHandle);
        int numParams = methodHandle.type().parameterCount();
        _parameters = new Object[numParams];
        for (int i = 0; i < numParams; i++)
        {
            _lookupTable.add(i);
        }
    }

    private void dropFromLookupTable(int index)
    {
        if (index < 0 || index >= _lookupTable.size())
            throw new IndexOutOfBoundsException();
        _lookupTable.remove(index);
    }

    private int getInternalIndex(int index)
    {
        if (index < 0 || index >= _lookupTable.size())
            throw new IndexOutOfBoundsException();
        return _lookupTable.get(index);
    }

    @Override
    public Object invoke(Object... args) throws Throwable
    {
        try
        {
            insertArguments(args);
            Object o = _methodHandle.invokeWithArguments(_parameters);
            if (_returnFilters != null)
            {
                for (MethodHandle filter : _returnFilters)
                {
                    o = filter.invoke(o);
                }
            }
            return o;
        }
        finally
        {
            clearArguments();
        }
    }

    @Override
    public MethodHolder bindTo(Object arg, int idx)
    {
        _parameters[getInternalIndex(idx)] = arg;
        dropFromLookupTable(idx);
        return this;
    }

    @Override
    public MethodHolder bindTo(Object arg)
    {
        return bindTo(arg, 0);
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    private MethodHolder insertArguments(Object... args)
    {
        if (_lookupTable.size() != args.length)
            throw new WrongMethodTypeException(String.format("Expected %s params but had %s", _lookupTable.size(), args.length));

        for (int i = 0; i < args.length; i++)
        {
            _parameters[_lookupTable.get(i)] = args[i];
        }
        return this;
    }

    private void clearArguments()
    {
        for (int i : _lookupTable)
        {
            _parameters[i] = null;
        }
    }

    @Override
    public MethodHolder filterReturnValue(MethodHandle filter)
    {
        if (_returnFilters == null)
            _returnFilters = new ArrayList<>();
        _returnFilters.add(filter);
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
        return ((_returnFilters == null) ? _methodHandle : _returnFilters.get(_returnFilters.size() - 1)).type().returnType();
    }
}
