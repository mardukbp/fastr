/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.ffi.codegen;

import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

import javax.lang.model.element.ExecutableElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.oracle.truffle.r.ffi.impl.upcalls.UpCallsRFFI;

/**
 * Generates code for the {@code rffi_upcallsindex.h} file, which defines a numeric constant for
 * each method of {@link FFIUpCallsIndexCodeGen}. Those constants are used to map C to Java
 * functions.
 */
public final class FFIUpCallsIndexCodeGen extends CodeGenBase {
    public static void main(String[] args) {
        new FFIUpCallsIndexCodeGen().run(args);
    }

    private void run(String[] args) {
        initOutput(args);
        out.printf("// GENERATED by %s class; DO NOT EDIT\n", FFIUpCallsIndexCodeGen.class.getName());
        out.println("// This file can be regenerated by running 'mx rfficodegen'");
        out.append("#ifndef RFFI_UPCALLSINDEX_H\n");
        out.append("#define RFFI_UPCALLSINDEX_H\n");
        out.append('\n');
        Method[] methods = UpCallsRFFI.class.getMethods();
        Arrays.sort(methods, new Comparator<Method>() {
            @Override
            public int compare(Method e1, Method e2) {
                return e1.getName().toString().compareTo(e2.getName().toString());
            }
        });
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            out.append("#define ").append(method.getName()).append("_x ").append(Integer.toString(i)).append('\n');
        }
        out.append('\n');
        out.append("#define ").append("UPCALLS_TABLE_SIZE ").append(Integer.toString(methods.length)).append('\n');
        out.append('\n');
        out.append("#endif // RFFI_UPCALLSINDEX_H\n");
    }
}
