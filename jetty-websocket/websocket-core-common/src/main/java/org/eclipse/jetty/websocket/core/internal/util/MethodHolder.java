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
 * An interface for managing invocations of methods whose arguments may need to be augmented, by
 * binding in certain parameters ahead of time.
 *
 * This differs from {@link MethodHandle}s that any calls the methods such as {@link #bindTo(Object)}
 * will change the instance of {@link MethodHolder} it is called on.
 *
 * Implementations of this may not be thread safe, so the caller must use some external mutual exclusion
 * unless they are using a specific implementation known to be thread-safe.
 */
public interface MethodHolder
{
    static MethodHolder from(MethodHandle methodHandle)
    {
        return from(methodHandle, false);
    }

    static MethodHolder from(MethodHandle methodHandle, boolean binding)
    {
        if (methodHandle == null)
            return null;
        return binding ? new BindingMethodHolder(methodHandle) : new NonBindingMethodHolder(methodHandle);
    }

    Object invoke(Object... args) throws Throwable;

    default MethodHolder bindTo(Object arg)
    {
        throw new UnsupportedOperationException();
    }

    default MethodHolder bindTo(Object arg, int idx)
    {
        throw new UnsupportedOperationException();
    }

    default MethodHolder filterReturnValue(MethodHandle filter)
    {
        throw new UnsupportedOperationException();
    }

    default MethodHolder changeReturnType(Class<Object> objectClass)
    {
        throw new UnsupportedOperationException();
    }

    default Class<?> parameterType(int idx)
    {
        throw new UnsupportedOperationException();
    }

    default Class<?> returnType()
    {
        throw new UnsupportedOperationException();
    }
}
