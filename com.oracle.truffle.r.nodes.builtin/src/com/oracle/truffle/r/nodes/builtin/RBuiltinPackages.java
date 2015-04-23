/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.builtin.base.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.env.*;
import com.oracle.truffle.r.runtime.env.REnvironment.PutException;
import com.oracle.truffle.r.runtime.ffi.*;
import com.oracle.truffle.r.runtime.ffi.DLL.DLLException;

/**
 * Support for loading built-in packages, currently limited to {@code base}.
 */
public final class RBuiltinPackages implements RBuiltinLookup {

    private static final RBuiltinPackages instance = new RBuiltinPackages();
    private static final RBuiltinPackage basePackage = new BasePackage();

    public static RBuiltinPackages getInstance() {
        return instance;
    }

    public static void loadBase(MaterializedFrame frame, boolean loadPackage) {
        RBuiltinPackage pkg = basePackage;
        REnvironment baseEnv = REnvironment.baseEnv();
        pkg.setEnv(baseEnv);
        BaseVariables.initialize(baseEnv);
        /*
         * All the RBuiltin PRIMITIVE methods that were created earlier need to be added to the
         * environment so that lookups through the environment work as expected.
         */
        Map<String, RBuiltinFactory> builtins = pkg.getBuiltins();
        for (Map.Entry<String, RBuiltinFactory> entrySet : builtins.entrySet()) {
            String methodName = entrySet.getKey();
            RBuiltinFactory builtinFactory = entrySet.getValue();
            if (builtinFactory.getKind() != RBuiltinKind.INTERNAL) {
                RFunction function = createFunction(builtinFactory, methodName);
                try {
                    baseEnv.put(methodName, function);
                    baseEnv.lockBinding(methodName);
                } catch (PutException ex) {
                    Utils.fail("failed to install builtin function: " + methodName);
                }
            }
        }
        if (!loadPackage) {
            return;
        }
        // Now "load" the package
        Path baseDirPath = FileSystems.getDefault().getPath(REnvVars.rHome(), "library", "base");
        Path basePathbase = baseDirPath.resolve("R").resolve("base");
        Source baseSource = null;
        try {
            baseSource = Source.fromFileName(basePathbase.toString());
        } catch (IOException ex) {
            Utils.fail(String.format("unable to open the base package %s", basePathbase));
        }
        // Load the (stub) DLL for base
        try {
            DLL.loadPackageDLL(baseDirPath.resolve("libs").resolve("base.so").toString(), true, true);
        } catch (DLLException ex) {
            Utils.fail(ex.getMessage());
        }
        // Any RBuiltinKind.SUBSTITUTE functions installed above should not be overridden
        try {
            HiddenInternalFunctions.MakeLazy.loadingBase = true;
            RContext.getEngine().parseAndEval(baseSource, frame, baseEnv, false, false);
        } finally {
            HiddenInternalFunctions.MakeLazy.loadingBase = false;
        }
        pkg.loadOverrides(frame, baseEnv);
    }

    public static void loadDefaultPackageOverrides() {
        RStringVector defPkgs = (RStringVector) ROptions.getValue("defaultPackages");
        for (int i = 0; i < defPkgs.getLength(); i++) {
            String pkgName = defPkgs.getDataAt(i);
            ArrayList<Source> componentList = RBuiltinPackage.getRFiles(pkgName);
            if (componentList == null) {
                continue;
            }
            /*
             * Only the overriding code can know which environment to update, package or namespace.
             */
            REnvironment env = REnvironment.baseEnv();
            for (Source source : componentList) {
                RContext.getEngine().parseAndEval(source, env.getFrame(), env, false, false);
            }
        }
    }

    @Override
    public RFunction lookup(String methodName) {
        RFunction function = RContext.getInstance().getCachedFunction(methodName);
        if (function != null) {
            return function;
        }

        RBuiltinFactory builtin = lookupBuiltin(methodName);
        if (builtin == null) {
            return null;
        }
        return createFunction(builtin, methodName);
    }

    private static RFunction createFunction(RBuiltinFactory builtinFactory, String methodName) {
        try {
            RootCallTarget callTarget = RBuiltinNode.createArgumentsCallTarget(builtinFactory);
            return RContext.getInstance().putCachedFunction(methodName, RDataFactory.createFunction(builtinFactory.getName(), callTarget, builtinFactory, REnvironment.baseEnv().getFrame()));
        } catch (Throwable t) {
            throw new RuntimeException("error while creating builtin " + methodName + " / " + builtinFactory, t);
        }
    }

    public static RBuiltinFactory lookupBuiltin(String name) {
        return basePackage.lookupByName(name);
    }

    /**
     * Used by {@link RDeparse} to detect whether a symbol is a builtin (or special), i.e. not an
     * {@link RBuiltinKind#INTERNAL}. N.B. special functions are not explicitly denoted currently,
     * only by virtue of the {@link RBuiltin#nonEvalArgs} attribute.
     */
    public boolean isPrimitiveBuiltin(String name) {
        RBuiltinPackage pkg = basePackage;
        RBuiltinDescriptor rbf = pkg.lookupByName(name);
        if (rbf != null && rbf.getKind() != RBuiltinKind.INTERNAL) {
            return true;
        }
        return false;
    }

}
