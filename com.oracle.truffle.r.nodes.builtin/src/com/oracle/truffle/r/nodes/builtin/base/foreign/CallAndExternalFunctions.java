/*
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.nodes.builtin.base.foreign;

import static com.oracle.truffle.r.runtime.RVisibility.CUSTOM;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;
import static com.oracle.truffle.r.runtime.context.FastROptions.UseInternalGridGraphics;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.library.fastrGrid.FastRGridExternalLookup;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.R_M_setPrimitiveMethodsNodeGen;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.R_externalPtrPrototypeObjectNodeGen;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.R_getClassFromCacheNodeGen;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.R_getGenericNodeGen;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.R_identCNodeGen;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.R_initMethodDispatchNodeGen;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.R_methodsPackageMetaNameNodeGen;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.R_nextMethodCallNodeGen;
import com.oracle.truffle.r.library.methods.MethodsListDispatchFactory.R_set_method_dispatchNodeGen;
import com.oracle.truffle.r.library.methods.SlotFactory.R_getSlotNodeGen;
import com.oracle.truffle.r.library.methods.SlotFactory.R_hasSlotNodeGen;
import com.oracle.truffle.r.library.methods.SlotFactory.R_setSlotNodeGen;
import com.oracle.truffle.r.library.methods.SubstituteDirectNodeGen;
import com.oracle.truffle.r.library.parallel.ParallelFunctionsFactory.MCIsChildNodeGen;
import com.oracle.truffle.r.library.stats.Approx;
import com.oracle.truffle.r.library.stats.ApproxTest;
import com.oracle.truffle.r.library.stats.BinDist;
import com.oracle.truffle.r.library.stats.CdistNodeGen;
import com.oracle.truffle.r.library.stats.CompleteCases;
import com.oracle.truffle.r.library.stats.CovcorNodeGen;
import com.oracle.truffle.r.library.stats.CutreeNodeGen;
import com.oracle.truffle.r.library.stats.DoubleCentreNodeGen;
import com.oracle.truffle.r.library.stats.Fmin;
import com.oracle.truffle.r.library.stats.Influence;
import com.oracle.truffle.r.library.stats.PPSum;
import com.oracle.truffle.r.library.stats.PPSum.PPSumExternal;
import com.oracle.truffle.r.library.stats.RMultinomNode;
import com.oracle.truffle.r.library.stats.RandFunctionsNodes;
import com.oracle.truffle.r.library.stats.RandFunctionsNodes.RandFunction1Node;
import com.oracle.truffle.r.library.stats.RandFunctionsNodes.RandFunction2Node;
import com.oracle.truffle.r.library.stats.RandFunctionsNodes.RandFunction3Node;
import com.oracle.truffle.r.library.stats.SignrankFreeNode;
import com.oracle.truffle.r.library.stats.SplineFunctionsFactory.SplineCoefNodeGen;
import com.oracle.truffle.r.library.stats.SplineFunctionsFactory.SplineEvalNodeGen;
import com.oracle.truffle.r.library.stats.StatsFunctionsNodes;
import com.oracle.truffle.r.library.stats.WilcoxFreeNode;
import com.oracle.truffle.r.library.stats.Zeroin2;
import com.oracle.truffle.r.library.stats.deriv.D;
import com.oracle.truffle.r.library.stats.deriv.Deriv;
import com.oracle.truffle.r.library.tools.C_ParseRdNodeGen;
import com.oracle.truffle.r.library.tools.DirChmodNodeGen;
import com.oracle.truffle.r.library.tools.Rmd5NodeGen;
import com.oracle.truffle.r.library.tools.ToolsTextFactory.CodeFilesAppendNodeGen;
import com.oracle.truffle.r.library.tools.ToolsTextFactory.DoTabExpandNodeGen;
import com.oracle.truffle.r.library.utils.CountFieldsNodeGen;
import com.oracle.truffle.r.library.utils.Crc64NodeGen;
import com.oracle.truffle.r.library.utils.DownloadNodeGen;
import com.oracle.truffle.r.library.utils.MenuNodeGen;
import com.oracle.truffle.r.library.utils.ObjectSizeNodeGen;
import com.oracle.truffle.r.library.utils.OctSizeNode;
import com.oracle.truffle.r.library.utils.RprofNodeGen;
import com.oracle.truffle.r.library.utils.RprofmemNodeGen;
import com.oracle.truffle.r.library.utils.TypeConvertNodeGen;
import com.oracle.truffle.r.library.utils.UnzipNodeGen;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.RInternalCodeBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.foreign.CallAndExternalFunctions.DotExternal.CallNamedFunctionNode;
import com.oracle.truffle.r.nodes.function.call.RExplicitCallNode;
import com.oracle.truffle.r.nodes.helpers.MaterializeNode;
import com.oracle.truffle.r.nodes.objects.GetPrimNameNodeGen;
import com.oracle.truffle.r.nodes.objects.NewObjectNodeGen;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalCode;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.builtins.RBehavior;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.builtins.RBuiltinKind;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RExternalPtr;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.ffi.CallRFFI;
import com.oracle.truffle.r.runtime.ffi.CallRFFI.InvokeCallNode;
import com.oracle.truffle.r.runtime.ffi.DLL;
import com.oracle.truffle.r.runtime.ffi.DLL.SymbolHandle;
import com.oracle.truffle.r.runtime.ffi.NativeCallInfo;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory;
import com.oracle.truffle.r.runtime.nmath.distr.Cauchy;
import com.oracle.truffle.r.runtime.nmath.distr.Cauchy.DCauchy;
import com.oracle.truffle.r.runtime.nmath.distr.Cauchy.PCauchy;
import com.oracle.truffle.r.runtime.nmath.distr.Cauchy.RCauchy;
import com.oracle.truffle.r.runtime.nmath.distr.Chisq;
import com.oracle.truffle.r.runtime.nmath.distr.Chisq.RChisq;
import com.oracle.truffle.r.runtime.nmath.distr.DBeta;
import com.oracle.truffle.r.runtime.nmath.distr.DGamma;
import com.oracle.truffle.r.runtime.nmath.distr.DHyper;
import com.oracle.truffle.r.runtime.nmath.distr.DNBeta;
import com.oracle.truffle.r.runtime.nmath.distr.DNBinom.DNBinomFunc;
import com.oracle.truffle.r.runtime.nmath.distr.DNBinom.DNBinomMu;
import com.oracle.truffle.r.runtime.nmath.distr.DNChisq;
import com.oracle.truffle.r.runtime.nmath.distr.DNorm;
import com.oracle.truffle.r.runtime.nmath.distr.DPois;
import com.oracle.truffle.r.runtime.nmath.distr.Dbinom;
import com.oracle.truffle.r.runtime.nmath.distr.Df;
import com.oracle.truffle.r.runtime.nmath.distr.Dnf;
import com.oracle.truffle.r.runtime.nmath.distr.Dnt;
import com.oracle.truffle.r.runtime.nmath.distr.Dt;
import com.oracle.truffle.r.runtime.nmath.distr.Exp.DExp;
import com.oracle.truffle.r.runtime.nmath.distr.Exp.PExp;
import com.oracle.truffle.r.runtime.nmath.distr.Exp.QExp;
import com.oracle.truffle.r.runtime.nmath.distr.Exp.RExp;
import com.oracle.truffle.r.runtime.nmath.distr.Geom;
import com.oracle.truffle.r.runtime.nmath.distr.Geom.DGeom;
import com.oracle.truffle.r.runtime.nmath.distr.Geom.RGeom;
import com.oracle.truffle.r.runtime.nmath.distr.LogNormal;
import com.oracle.truffle.r.runtime.nmath.distr.LogNormal.DLNorm;
import com.oracle.truffle.r.runtime.nmath.distr.LogNormal.PLNorm;
import com.oracle.truffle.r.runtime.nmath.distr.LogNormal.QLNorm;
import com.oracle.truffle.r.runtime.nmath.distr.Logis;
import com.oracle.truffle.r.runtime.nmath.distr.Logis.DLogis;
import com.oracle.truffle.r.runtime.nmath.distr.Logis.RLogis;
import com.oracle.truffle.r.runtime.nmath.distr.PGamma;
import com.oracle.truffle.r.runtime.nmath.distr.PHyper;
import com.oracle.truffle.r.runtime.nmath.distr.PNBeta;
import com.oracle.truffle.r.runtime.nmath.distr.PNBinom.PNBinomFunc;
import com.oracle.truffle.r.runtime.nmath.distr.PNBinom.PNBinomMu;
import com.oracle.truffle.r.runtime.nmath.distr.PNChisq;
import com.oracle.truffle.r.runtime.nmath.distr.PPois;
import com.oracle.truffle.r.runtime.nmath.distr.PTukey;
import com.oracle.truffle.r.runtime.nmath.distr.Pbeta;
import com.oracle.truffle.r.runtime.nmath.distr.Pbinom;
import com.oracle.truffle.r.runtime.nmath.distr.Pf;
import com.oracle.truffle.r.runtime.nmath.distr.Pnf;
import com.oracle.truffle.r.runtime.nmath.distr.Pnorm;
import com.oracle.truffle.r.runtime.nmath.distr.Pnt;
import com.oracle.truffle.r.runtime.nmath.distr.Pt;
import com.oracle.truffle.r.runtime.nmath.distr.QBeta;
import com.oracle.truffle.r.runtime.nmath.distr.QGamma;
import com.oracle.truffle.r.runtime.nmath.distr.QHyper;
import com.oracle.truffle.r.runtime.nmath.distr.QNBeta;
import com.oracle.truffle.r.runtime.nmath.distr.QNBinom.QNBinomFunc;
import com.oracle.truffle.r.runtime.nmath.distr.QNBinom.QNBinomMu;
import com.oracle.truffle.r.runtime.nmath.distr.QNChisq;
import com.oracle.truffle.r.runtime.nmath.distr.QPois;
import com.oracle.truffle.r.runtime.nmath.distr.QTukey;
import com.oracle.truffle.r.runtime.nmath.distr.Qbinom;
import com.oracle.truffle.r.runtime.nmath.distr.Qf;
import com.oracle.truffle.r.runtime.nmath.distr.Qnf;
import com.oracle.truffle.r.runtime.nmath.distr.Qnorm;
import com.oracle.truffle.r.runtime.nmath.distr.Qnt;
import com.oracle.truffle.r.runtime.nmath.distr.Qt;
import com.oracle.truffle.r.runtime.nmath.distr.RBeta;
import com.oracle.truffle.r.runtime.nmath.distr.RGamma;
import com.oracle.truffle.r.runtime.nmath.distr.RHyper;
import com.oracle.truffle.r.runtime.nmath.distr.RNBinom.RNBinomFunc;
import com.oracle.truffle.r.runtime.nmath.distr.RNBinom.RNBinomMu;
import com.oracle.truffle.r.runtime.nmath.distr.RNchisq;
import com.oracle.truffle.r.runtime.nmath.distr.RPois;
import com.oracle.truffle.r.runtime.nmath.distr.Rbinom;
import com.oracle.truffle.r.runtime.nmath.distr.Rf;
import com.oracle.truffle.r.runtime.nmath.distr.Rnorm;
import com.oracle.truffle.r.runtime.nmath.distr.Rt;
import com.oracle.truffle.r.runtime.nmath.distr.Signrank.DSignrank;
import com.oracle.truffle.r.runtime.nmath.distr.Signrank.PSignrank;
import com.oracle.truffle.r.runtime.nmath.distr.Signrank.QSignrank;
import com.oracle.truffle.r.runtime.nmath.distr.Signrank.RSignrank;
import com.oracle.truffle.r.runtime.nmath.distr.Unif.DUnif;
import com.oracle.truffle.r.runtime.nmath.distr.Unif.PUnif;
import com.oracle.truffle.r.runtime.nmath.distr.Unif.QUnif;
import com.oracle.truffle.r.runtime.nmath.distr.Unif.Runif;
import com.oracle.truffle.r.runtime.nmath.distr.Weibull.DWeibull;
import com.oracle.truffle.r.runtime.nmath.distr.Weibull.PWeibull;
import com.oracle.truffle.r.runtime.nmath.distr.Weibull.QWeibull;
import com.oracle.truffle.r.runtime.nmath.distr.Weibull.RWeibull;
import com.oracle.truffle.r.runtime.nmath.distr.Wilcox.DWilcox;
import com.oracle.truffle.r.runtime.nmath.distr.Wilcox.PWilcox;
import com.oracle.truffle.r.runtime.nmath.distr.Wilcox.QWilcox;
import com.oracle.truffle.r.runtime.nmath.distr.Wilcox.RWilcox;

/**
 * {@code .Call}, {@code .Call.graphics}, {@code .External}, {@code .External2},
 * {@code External.graphics} functions, which share a common signature.
 *
 * TODO Completeness (more types, more error checks), Performance (copying). Especially all the
 * subtleties around copying.
 *
 * See <a href="https://stat.ethz.ch/R-manual/R-devel/library/base/html/Foreign.html">here</a>.
 */
public class CallAndExternalFunctions {

    @TruffleBoundary
    private static Object encodeArgumentPairList(RArgsValuesAndNames args, String symbolName) {
        Object list = RNull.instance;
        for (int i = args.getLength() - 1; i >= 0; i--) {
            String name = args.getSignature().getName(i);
            list = RDataFactory.createPairList(args.getArgument(i), list, name == null ? RNull.instance : RDataFactory.createSymbolInterned(name));
        }
        list = RDataFactory.createPairList(symbolName, list);
        return list;
    }

    /**
     * Handles the generic case, but also many special case functions that are called from the
     * default packages.
     *
     * The native function to be called can be specified in two ways:
     * <ol>
     * <li>as an object of R class {@code NativeSymbolInfo} (passed as an {@link RList}. In this
     * case {@code .PACKAGE} is ignored even if provided.</li>
     * <li>as a character string. If {@code .PACKAGE} is provided the search is restricted to that
     * package, else the symbol is searched in all loaded packages (evidently dangerous as the
     * symbol could be duplicated)</li>
     * </ol>
     * Many of the functions in the builtin packages have been translated to Java which is handled
     * by specializations that {@link #lookupBuiltin(RList)}. N.N. In principle such a function
     * could be invoked by a string but experimentally that situation has never been encountered.
     */
    @RBuiltin(name = ".Call", kind = PRIMITIVE, parameterNames = {".NAME", "...", "PACKAGE"}, behavior = COMPLEX)
    public abstract static class DotCall extends Dot {

        @Child private MaterializeNode materializeNode = MaterializeNode.create(true);

        static {
            Casts.noCasts(DotCall.class);
        }

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RMissing.instance, RArgsValuesAndNames.EMPTY, RMissing.instance};
        }

        private Object[] materializeArgs(Object[] args) {
            Object[] materializedArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                materializedArgs[i] = materializeNode.execute(args[i]);
            }
            return materializedArgs;
        }

        @Override
        @TruffleBoundary
        public RExternalBuiltinNode lookupBuiltin(RList symbol) {
            String name = lookupName(symbol);
            if (RContext.getInstance().getOption(UseInternalGridGraphics) && name != null) {
                RExternalBuiltinNode gridExternal = FastRGridExternalLookup.lookupDotCall(name);
                if (gridExternal != null) {
                    return gridExternal;
                }
            }
            switch (name) {
                // methods
                case "R_initMethodDispatch":
                    return R_initMethodDispatchNodeGen.create();
                case "R_methodsPackageMetaName":
                    return R_methodsPackageMetaNameNodeGen.create();
                case "R_set_method_dispatch":
                    return R_set_method_dispatchNodeGen.create();
                case "R_M_setPrimitiveMethods":
                    return R_M_setPrimitiveMethodsNodeGen.create();
                case "R_getClassFromCache":
                    return R_getClassFromCacheNodeGen.create();
                case "R_clear_method_selection":
                case "R_dummy_extern_place":
                case "R_el_named":
                    return new UnimplementedExternal(name);
                case "R_externalptr_prototype_object":
                    return R_externalPtrPrototypeObjectNodeGen.create();
                case "R_getGeneric":
                    return R_getGenericNodeGen.create();
                case "R_get_slot":
                    return R_getSlotNodeGen.create();
                case "R_hasSlot":
                    return R_hasSlotNodeGen.create();
                case "R_identC":
                    return R_identCNodeGen.create();
                case "R_methods_test_MAKE_CLASS":
                case "R_methods_test_NEW":
                case "R_missingArg":
                case "R_nextMethodCall":
                    return R_nextMethodCallNodeGen.create();
                case "R_quick_method_check":
                case "R_selectMethod":
                case "R_set_el_named":
                    return new UnimplementedExternal(name);
                case "R_set_slot":
                    return R_setSlotNodeGen.create();
                case "R_standardGeneric":
                    return new UnimplementedExternal(name);
                case "do_substitute_direct":
                    return SubstituteDirectNodeGen.create();
                case "Rf_allocS4Object":
                    return new UnimplementedExternal(name);
                case "R_get_primname":
                    return GetPrimNameNodeGen.create();
                case "new_object":
                    return NewObjectNodeGen.create();

                // stats

                case "fft":
                    return FftNodeGen.create();
                case "cov":
                    return CovcorNodeGen.create(false);
                case "cor":
                    return CovcorNodeGen.create(true);
                case "SplineCoef":
                    return SplineCoefNodeGen.create();
                case "SplineEval":
                    return SplineEvalNodeGen.create();
                case "pnorm":
                    return StatsFunctionsNodes.Function3_2Node.create(new Pnorm());
                case "qnorm":
                    return StatsFunctionsNodes.Function3_2Node.create(new Qnorm());
                case "rnorm":
                    return RandFunction2Node.createDouble(Rnorm::new);
                case "runif":
                    return RandFunction2Node.createDouble(Runif::new);
                case "rbeta":
                    return RandFunction2Node.createDouble(RBeta::new);
                case "rgamma":
                    return RandFunction2Node.createDouble(RGamma::new);
                case "rcauchy":
                    return RandFunction2Node.createDouble(RCauchy::new);
                case "rf":
                    return RandFunction2Node.createDouble(Rf::new);
                case "rlogis":
                    return RandFunction2Node.createDouble(RLogis::new);
                case "rweibull":
                    return RandFunction2Node.createDouble(RWeibull::new);
                case "rnchisq":
                    return RandFunction2Node.createDouble(RNchisq::new);
                case "rnbinom_mu":
                    return RandFunction2Node.createDouble(RNBinomMu::new);
                case "rwilcox":
                    return RandFunction2Node.createInt(RWilcox::new);
                case "rchisq":
                    return RandFunction1Node.createDouble(RChisq::new);
                case "rexp":
                    return RandFunction1Node.createDouble(RExp::new);
                case "rgeom":
                    return RandFunction1Node.createInt(RGeom::new);
                case "rpois":
                    return RandFunction1Node.createInt(RPois::new);
                case "rnbinom":
                    return RandFunction2Node.createInt(RNBinomFunc::new);
                case "rt":
                    return RandFunction1Node.createDouble(Rt::new);
                case "rsignrank":
                    return RandFunction1Node.createInt(RSignrank::new);
                case "rhyper":
                    return RandFunction3Node.createInt(RHyper::new);
                case "phyper":
                    return StatsFunctionsNodes.Function4_2Node.create(new PHyper());
                case "dhyper":
                    return StatsFunctionsNodes.Function4_1Node.create(new DHyper());
                case "qhyper":
                    return StatsFunctionsNodes.Function4_2Node.create(new QHyper());
                case "pnchisq":
                    return StatsFunctionsNodes.Function3_2Node.create(new PNChisq());
                case "qnchisq":
                    return StatsFunctionsNodes.Function3_2Node.create(new QNChisq());
                case "dnchisq":
                    return StatsFunctionsNodes.Function3_1Node.create(new DNChisq());
                case "qt":
                    return StatsFunctionsNodes.Function2_2Node.create(new Qt());
                case "pt":
                    return StatsFunctionsNodes.Function2_2Node.create(new Pt());
                case "qgamma":
                    return StatsFunctionsNodes.Function3_2Node.create(new QGamma());
                case "dbinom":
                    return StatsFunctionsNodes.Function3_1Node.create(new Dbinom());
                case "qbinom":
                    return StatsFunctionsNodes.Function3_2Node.create(new Qbinom());
                case "punif":
                    return StatsFunctionsNodes.Function3_2Node.create(new PUnif());
                case "dunif":
                    return StatsFunctionsNodes.Function3_1Node.create(new DUnif());
                case "qunif":
                    return StatsFunctionsNodes.Function3_2Node.create(new QUnif());
                case "ppois":
                    return StatsFunctionsNodes.Function2_2Node.create(new PPois());
                case "qpois":
                    return StatsFunctionsNodes.Function2_2Node.create(new QPois());
                case "qweibull":
                    return StatsFunctionsNodes.Function3_2Node.create(new QWeibull());
                case "pweibull":
                    return StatsFunctionsNodes.Function3_2Node.create(new PWeibull());
                case "dweibull":
                    return StatsFunctionsNodes.Function3_1Node.create(new DWeibull());
                case "rbinom":
                    return RandFunction2Node.createInt(Rbinom::new);
                case "pbinom":
                    return StatsFunctionsNodes.Function3_2Node.create(new Pbinom());
                case "pbeta":
                    return StatsFunctionsNodes.Function3_2Node.create(new Pbeta());
                case "qbeta":
                    return StatsFunctionsNodes.Function3_2Node.create(new QBeta());
                case "dcauchy":
                    return StatsFunctionsNodes.Function3_1Node.create(new DCauchy());
                case "pcauchy":
                    return StatsFunctionsNodes.Function3_2Node.create(new PCauchy());
                case "qcauchy":
                    return StatsFunctionsNodes.Function3_2Node.create(new Cauchy.QCauchy());
                case "pf":
                    return StatsFunctionsNodes.Function3_2Node.create(new Pf());
                case "qf":
                    return StatsFunctionsNodes.Function3_2Node.create(new Qf());
                case "df":
                    return StatsFunctionsNodes.Function3_1Node.create(new Df());
                case "dgamma":
                    return StatsFunctionsNodes.Function3_1Node.create(new DGamma());
                case "pgamma":
                    return StatsFunctionsNodes.Function3_2Node.create(new PGamma());
                case "dchisq":
                    return StatsFunctionsNodes.Function2_1Node.create(new Chisq.DChisq());
                case "qchisq":
                    return StatsFunctionsNodes.Function2_2Node.create(new Chisq.QChisq());
                case "qgeom":
                    return StatsFunctionsNodes.Function2_2Node.create(new Geom.QGeom());
                case "pchisq":
                    return StatsFunctionsNodes.Function2_2Node.create(new Chisq.PChisq());
                case "dexp":
                    return StatsFunctionsNodes.Function2_1Node.create(new DExp());
                case "pexp":
                    return StatsFunctionsNodes.Function2_2Node.create(new PExp());
                case "qexp":
                    return StatsFunctionsNodes.Function2_2Node.create(new QExp());
                case "dgeom":
                    return StatsFunctionsNodes.Function2_1Node.create(new DGeom());
                case "dpois":
                    return StatsFunctionsNodes.Function2_1Node.create(new DPois());
                case "dbeta":
                    return StatsFunctionsNodes.Function3_1Node.create(new DBeta());
                case "dnbeta":
                    return StatsFunctionsNodes.Function4_1Node.create(new DNBeta());
                case "qnbeta":
                    return StatsFunctionsNodes.Function4_2Node.create(new QNBeta());
                case "dnf":
                    return StatsFunctionsNodes.Function4_1Node.create(new Dnf());
                case "qnf":
                    return StatsFunctionsNodes.Function4_2Node.create(new Qnf());
                case "pnf":
                    return StatsFunctionsNodes.Function4_2Node.create(new Pnf());
                case "pnbeta":
                    return StatsFunctionsNodes.Function4_2Node.create(new PNBeta());
                case "dt":
                    return StatsFunctionsNodes.Function2_1Node.create(new Dt());
                case "rlnorm":
                    return RandFunction2Node.createDouble(LogNormal.RLNorm::new);
                case "dlnorm":
                    return StatsFunctionsNodes.Function3_1Node.create(new DLNorm());
                case "qlnorm":
                    return StatsFunctionsNodes.Function3_2Node.create(new QLNorm());
                case "plnorm":
                    return StatsFunctionsNodes.Function3_2Node.create(new PLNorm());
                case "dlogis":
                    return StatsFunctionsNodes.Function3_1Node.create(new DLogis());
                case "qlogis":
                    return StatsFunctionsNodes.Function3_2Node.create(new Logis.QLogis());
                case "plogis":
                    return StatsFunctionsNodes.Function3_2Node.create(new Logis.PLogis());
                case "pgeom":
                    return StatsFunctionsNodes.Function2_2Node.create(new Geom.PGeom());
                case "qnbinom":
                    return StatsFunctionsNodes.Function3_2Node.create(new QNBinomFunc());
                case "dnbinom":
                    return StatsFunctionsNodes.Function3_1Node.create(new DNBinomFunc());
                case "pnbinom":
                    return StatsFunctionsNodes.Function3_2Node.create(new PNBinomFunc());
                case "qnbinom_mu":
                    return StatsFunctionsNodes.Function3_2Node.create(new QNBinomMu());
                case "dnbinom_mu":
                    return StatsFunctionsNodes.Function3_1Node.create(new DNBinomMu());
                case "pnbinom_mu":
                    return StatsFunctionsNodes.Function3_2Node.create(new PNBinomMu());
                case "qwilcox":
                    return StatsFunctionsNodes.Function3_2Node.create(new QWilcox());
                case "pwilcox":
                    return StatsFunctionsNodes.Function3_2Node.create(new PWilcox());
                case "dwilcox":
                    return StatsFunctionsNodes.Function3_1Node.create(new DWilcox());
                case "dsignrank":
                    return StatsFunctionsNodes.Function2_1Node.create(new DSignrank());
                case "psignrank":
                    return StatsFunctionsNodes.Function2_2Node.create(new PSignrank());
                case "dnt":
                    return StatsFunctionsNodes.Function3_1Node.create(new Dnt());
                case "pnt":
                    return StatsFunctionsNodes.Function3_2Node.create(new Pnt());
                case "qnt":
                    return StatsFunctionsNodes.Function3_2Node.create(new Qnt());
                case "qsignrank":
                    return StatsFunctionsNodes.Function2_2Node.create(new QSignrank());
                case "qtukey":
                    return StatsFunctionsNodes.Function4_2Node.create(new QTukey());
                case "ptukey":
                    return StatsFunctionsNodes.Function4_2Node.create(new PTukey());
                case "rmultinom":
                    return RMultinomNode.create();
                case "Approx":
                    return Approx.create();
                case "ApproxTest":
                    return ApproxTest.create();
                case "Cdist":
                    return CdistNodeGen.create();
                case "DoubleCentre":
                    return DoubleCentreNodeGen.create();
                case "cutree":
                    return CutreeNodeGen.create();
                case "BinDist":
                    return BinDist.create();
                case "influence":
                    return Influence.create();
                case "mvfft":
                    // TODO: only transforms arguments and then calls already ported fft
                    return new UnimplementedExternal(name);
                case "nextn":
                    // TODO: do not want to pull in fourier.c, should be simple to port
                    return new UnimplementedExternal(name);
                case "r2dtable":
                    // TODO: do not want to pull in random.c + uses PutRNG(), we can pull in rcont.c
                    // and then this
                    // becomes simple wrapper around it.
                    return new UnimplementedExternal(name);
                case "Fisher_sim":
                case "chisq_sim":
                    // TODO: uses PutRNG(), with rcont.c may become moderately difficult to port
                    return new UnimplementedExternal(name);
                case "Rsm":
                    return new UnimplementedExternal(name);
                case "optim":
                case "optimhess":
                case "dqagi":
                case "dqags":
                case "nlm":
                    // TODO: file optim.c uses Defn.h with non public RFFI API
                    // It seems that Defn.h can be replaced with Rinternals.h
                    // From GNUR R core it pulls few aux macros like F77_CALL, we can pull those
                    // individually
                    // Furthermore it requires to pull lbfgsb.c and linkpack (Appl/Linpack.h)
                    // routines from core

                case "pp_sum":
                    return PPSumExternal.create();
                case "intgrt_vec":
                    return PPSum.IntgrtVecNode.create();

                case "updateform":
                    return getExternalModelBuiltinNode("updateform");

                case "Cdqrls":
                    return new RInternalCodeBuiltinNode("stats", RInternalCode.loadSourceRelativeTo(RandFunctionsNodes.class, "lm.R"), "Cdqrls");

                case "dnorm":
                    return StatsFunctionsNodes.Function3_1Node.create(new DNorm());

                // tools
                case "doTabExpand":
                    return DoTabExpandNodeGen.create();
                case "codeFilesAppend":
                    return CodeFilesAppendNodeGen.create();
                case "Rmd5":
                    return Rmd5NodeGen.create();
                case "dirchmod":
                    return DirChmodNodeGen.create();
                case "delim_match":
                case "C_getfmts":
                case "check_nonASCII":
                case "check_nonASCII2":
                case "ps_kill":
                case "ps_sigs":
                case "ps_priority":
                case "startHTTPD":
                case "stopHTTPD":
                case "C_deparseRd":
                    return new UnimplementedExternal(name);

                // utils
                case "crc64":
                    return Crc64NodeGen.create();
                case "flushconsole":
                    return new Flushconsole();
                case "menu":
                    return MenuNodeGen.create();
                case "nsl":
                    return new UnimplementedExternal(name);
                case "objectSize":
                    return ObjectSizeNodeGen.create();
                case "octsize":
                    return OctSizeNode.create();
                case "processevents":
                case "sockconnect":
                case "sockread":
                case "sockclose":
                case "sockopen":
                case "socklisten":
                case "sockwrite":
                    return new UnimplementedExternal(name);

                // parallel
                case "mc_is_child":
                    return MCIsChildNodeGen.create();
                default:
                    return null;
            }
            // Note: some externals that may be ported with reasonable effort
            // tukeyline, rfilter, SWilk, acf, Burg, d2x2xk, pRho
        }

        /**
         * {@code .NAME = NativeSymbolInfo} implemented as a builtin.
         */
        @SuppressWarnings("unused")
        @Specialization(limit = "99", guards = {"cached == symbol", "builtin != null"})
        protected Object doExternal(VirtualFrame frame, RList symbol, RArgsValuesAndNames args, Object packageName,
                        @Cached("symbol") RList cached,
                        @Cached("lookupBuiltin(symbol)") RExternalBuiltinNode builtin) {
            return builtin.call(frame, args);
        }

        /**
         * {@code .NAME = NativeSymbolInfo} implementation remains in native code (e.g. non-builtin
         * package)
         */
        @SuppressWarnings("unused")
        @Specialization(limit = "getCacheSize(2)", guards = {"cached == symbol", "builtin == null"})
        protected Object callSymbolInfoFunction(VirtualFrame frame, RList symbol, RArgsValuesAndNames args, Object packageName,
                        @Cached("symbol") RList cached,
                        @Cached("lookupBuiltin(symbol)") RExternalBuiltinNode builtin,
                        @Cached("new()") ExtractNativeCallInfoNode extractSymbolInfo,
                        @Cached("extractSymbolInfo.execute(symbol)") NativeCallInfo nativeCallInfo,
                        @Cached("createBinaryProfile()") ConditionProfile registeredProfile) {
            if (registeredProfile.profile(isRegisteredRFunction(nativeCallInfo))) {
                return explicitCall(frame, nativeCallInfo, args);
            } else {
                return dispatch(frame, nativeCallInfo, materializeArgs(args.getArguments()));
            }
        }

        /**
         * For some reason, the list instance may change, although it carries the same info. For
         * such cases there is this generic version.
         */
        @Specialization(replaces = {"callSymbolInfoFunction", "doExternal"})
        protected Object callSymbolInfoFunctionGeneric(VirtualFrame frame, RList symbol, RArgsValuesAndNames args, @SuppressWarnings("unused") Object packageName,
                        @Cached("new()") ExtractNativeCallInfoNode extractSymbolInfo,
                        @Cached("createBinaryProfile()") ConditionProfile registeredProfile) {
            RExternalBuiltinNode builtin = lookupBuiltin(symbol);
            if (builtin != null) {
                throw RInternalError.shouldNotReachHere("Cache for .Calls with FastR reimplementation (lookupBuiltin(...) != null) exceeded the limit");
            }
            NativeCallInfo nativeCallInfo = extractSymbolInfo.execute(symbol);
            if (registeredProfile.profile(isRegisteredRFunction(nativeCallInfo))) {
                return explicitCall(frame, nativeCallInfo, args);
            } else {
                return dispatch(frame, nativeCallInfo, materializeArgs(args.getArguments()));
            }
        }

        /**
         * {@code .NAME = string}, no package specified.
         */
        @Specialization
        protected Object callNamedFunction(VirtualFrame frame, String symbol, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName,
                        @Cached("createRegisteredNativeSymbol(CallNST)") DLL.RegisteredNativeSymbol rns,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode,
                        @Cached("createBinaryProfile()") ConditionProfile registeredProfile) {
            return callNamedFunctionWithPackage(frame, symbol, args, null, rns, findSymbolNode, registeredProfile);
        }

        /**
         * {@code .NAME = string, .PACKAGE = package}. An error if package provided and it does not
         * define that symbol.
         */
        @Specialization
        protected Object callNamedFunctionWithPackage(VirtualFrame frame, String symbol, RArgsValuesAndNames args, String packageName,
                        @Cached("createRegisteredNativeSymbol(CallNST)") DLL.RegisteredNativeSymbol rns,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode,
                        @Cached("createBinaryProfile()") ConditionProfile registeredProfile) {
            DLL.SymbolHandle func = findSymbolNode.execute(symbol, packageName, rns);
            if (func == DLL.SYMBOL_NOT_FOUND) {
                throw error(RError.Message.SYMBOL_NOT_IN_TABLE, symbol, "Call", packageName);
            }
            if (registeredProfile.profile(isRegisteredRFunction(func))) {
                return explicitCall(frame, func, args);
            } else {
                return dispatch(frame, new NativeCallInfo(symbol, func, rns.getDllInfo()), materializeArgs(args.getArguments()));
            }
        }

        @Specialization
        protected Object callExternalPtrFunction(VirtualFrame frame, RExternalPtr symbol, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName,
                        @Cached("createBinaryProfile()") ConditionProfile registeredProfile) {
            if (registeredProfile.profile(isRegisteredRFunction(symbol))) {
                return explicitCall(frame, symbol, args);
            } else {
                return dispatch(frame, new NativeCallInfo("", symbol.getAddr(), null), materializeArgs(args.getArguments()));
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object dotCallFallback(Object symbol, Object args, Object packageName) {
            throw fallback(this, symbol);
        }
    }

    /**
     * The interpretation of the {@code .NAME} and {code .PACKAGE} arguments as are for
     * {@link DotCall}.
     */
    @com.oracle.truffle.r.runtime.builtins.RBuiltin(name = ".External", kind = RBuiltinKind.PRIMITIVE, parameterNames = {".NAME", "...", "PACKAGE"}, behavior = RBehavior.COMPLEX)
    public abstract static class DotExternal extends Dot {

        static {
            Casts.noCasts(DotExternal.class);
        }

        @Override
        @TruffleBoundary
        public RExternalBuiltinNode lookupBuiltin(RList f) {
            String name = lookupName(f);
            if (RContext.getInstance().getOption(UseInternalGridGraphics)) {
                RExternalBuiltinNode gridExternal = FastRGridExternalLookup.lookupDotExternal(name);
                if (gridExternal != null) {
                    return gridExternal;
                }
            }
            switch (name) {
                case "compcases":
                    return new CompleteCases();
                // stats
                case "doD":
                    return D.create();
                case "deriv":
                    return Deriv.create();
                // utils
                case "countfields":
                    return CountFieldsNodeGen.create();
                case "readtablehead":
                    return ReadTableHeadNodeGen.create();
                case "download":
                    return DownloadNodeGen.create();
                case "termsform":
                    return getExternalModelBuiltinNode("termsform");
                case "Rprof":
                    return RprofNodeGen.create();
                case "Rprofmem":
                    return RprofmemNodeGen.create();
                case "wilcox_free":
                    return new WilcoxFreeNode();
                case "signrank_free":
                    return new SignrankFreeNode();
                case "unzip":
                    return UnzipNodeGen.create();
                case "addhistory":
                case "loadhistory":
                case "savehistory":
                case "dataentry":
                case "dataviewer":
                case "edit":
                case "fileedit":
                case "selectlist":
                    return new UnimplementedExternal(name);
                default:
                    return null;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "1", guards = {"cached == symbol", "builtin != null"})
        protected Object doExternal(VirtualFrame frame, RList symbol, RArgsValuesAndNames args, Object packageName,
                        @Cached("symbol") RList cached,
                        @Cached("lookupBuiltin(symbol)") RExternalBuiltinNode builtin) {
            return builtin.call(frame, args);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "1", guards = {"cached.symbol == symbol", "builtin == null"})
        protected Object callNamedFunction(VirtualFrame frame, RList symbol, RArgsValuesAndNames args, Object packageName,
                        @Cached("lookupBuiltin(symbol)") RExternalBuiltinNode builtin,
                        @Cached("new(symbol)") CallNamedFunctionNode cached,
                        @Cached("createBinaryProfile()") ConditionProfile registeredProfile) {
            if (registeredProfile.profile(isRegisteredRFunction(cached.nativeCallInfo))) {
                return explicitCall(frame, cached.nativeCallInfo, args);
            } else {
                Object list = encodeArgumentPairList(args, cached.nativeCallInfo.name);
                return dispatch(frame, cached.nativeCallInfo, new Object[]{list});
            }
        }

        public static class CallNamedFunctionNode extends Node {
            public final RList symbol;
            public final NativeCallInfo nativeCallInfo;

            public CallNamedFunctionNode(RList symbol) {
                this.symbol = symbol;
                this.nativeCallInfo = new ExtractNativeCallInfoNode().execute(symbol);
            }

        }

        @Specialization
        protected Object callNamedFunction(VirtualFrame frame, String symbol, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName,
                        @Cached("createRegisteredNativeSymbol(ExternalNST)") DLL.RegisteredNativeSymbol rns,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode,
                        @Cached("createBinaryProfile()") ConditionProfile registeredProfile) {
            return callNamedFunctionWithPackage(frame, symbol, args, null, rns, findSymbolNode, registeredProfile);
        }

        @Specialization
        protected Object callNamedFunctionWithPackage(VirtualFrame frame, String symbol, RArgsValuesAndNames args, String packageName,
                        @Cached("createRegisteredNativeSymbol(ExternalNST)") DLL.RegisteredNativeSymbol rns,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode,
                        @Cached("createBinaryProfile()") ConditionProfile registeredProfile) {
            DLL.SymbolHandle func = findSymbolNode.execute(symbol, packageName, rns);
            if (func == DLL.SYMBOL_NOT_FOUND) {
                throw error(RError.Message.SYMBOL_NOT_IN_TABLE, symbol, "External", packageName);
            }
            if (registeredProfile.profile(isRegisteredRFunction(func))) {
                return explicitCall(frame, func, args);
            } else {
                Object list = encodeArgumentPairList(args, symbol);
                return dispatch(frame, new NativeCallInfo(symbol, func, rns.getDllInfo()), new Object[]{list});
            }
        }

        @Fallback
        protected Object fallback(Object f, @SuppressWarnings("unused") Object args, @SuppressWarnings("unused") Object packageName) {
            throw fallback(this, f);
        }
    }

    @RBuiltin(name = ".External2", visibility = CUSTOM, kind = PRIMITIVE, parameterNames = {".NAME", "...", "PACKAGE"}, behavior = COMPLEX)
    public abstract static class DotExternal2 extends Dot {
        private static final Object CALL = "call";
        /**
         * This argument for the native function should be SPECIALSXP reprenting the .External2
         * builtin. In GnuR SPECIALSXP is index into the table of builtins. External2 and External
         * are in fact one native function with two entries in this table, the "op" argument is used
         * to determine whether the call was made to .External or .External2. The actual code of the
         * native function that is eventually invoked will always get SPECIALSXP reprenting the
         * .External2, becuase functions exported as .External do not take the "op" argument.
         */
        @CompilationFinal private Object op = null;

        static {
            Casts.noCasts(DotExternal2.class);
        }

        private Object getOp() {
            if (op == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                op = RContext.getInstance().lookupBuiltin(".External2");
            }
            return op;
        }

        @Override
        @TruffleBoundary
        public RExternalBuiltinNode lookupBuiltin(RList symbol) {
            String name = lookupName(symbol);
            if (RContext.getInstance().getOption(UseInternalGridGraphics)) {
                RExternalBuiltinNode gridExternal = FastRGridExternalLookup.lookupDotExternal2(name);
                if (gridExternal != null) {
                    return gridExternal;
                }
            }
            switch (name) {
                // tools
                case "writetable":
                    return WriteTableNodeGen.create();
                case "typeconvert":
                    return TypeConvertNodeGen.create();
                case "C_parseRd":
                    return C_ParseRdNodeGen.create();
                case "modelmatrix":
                case "modelframe":
                    return getExternalModelBuiltinNode(name);
                case "zeroin2":
                    return Zeroin2.create();

                // stats:
                case "do_fmin":
                    return Fmin.create();

                default:
                    return null;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "1", guards = {"cached == symbol", "builtin != null"})
        protected Object doExternal(VirtualFrame frame, RList symbol, RArgsValuesAndNames args, Object packageName,
                        @Cached("symbol") RList cached,
                        @Cached("lookupBuiltin(symbol)") RExternalBuiltinNode builtin) {
            return builtin.call(frame, args);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "1", guards = {"cached.symbol == symbol", "builtin == null"})
        protected Object callNamedFunction(VirtualFrame frame, RList symbol, RArgsValuesAndNames args, Object packageName,
                        @Cached("lookupBuiltin(symbol)") RExternalBuiltinNode builtin,
                        @Cached("new(symbol)") CallNamedFunctionNode cached) {
            Object list = encodeArgumentPairList(args, cached.nativeCallInfo.name);
            REnvironment rho = REnvironment.frameToEnvironment(frame.materialize());
            return dispatch(frame, cached.nativeCallInfo, new Object[]{CALL, getOp(), list, rho});
        }

        @Specialization
        protected Object callNamedFunction(VirtualFrame frame, String symbol, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName,
                        @Cached("createRegisteredNativeSymbol(ExternalNST)") DLL.RegisteredNativeSymbol rns,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode) {
            return callNamedFunctionWithPackage(frame, symbol, args, null, rns, findSymbolNode);
        }

        @Specialization
        protected Object callNamedFunctionWithPackage(VirtualFrame frame, String symbol, RArgsValuesAndNames args, String packageName,
                        @Cached("createRegisteredNativeSymbol(ExternalNST)") DLL.RegisteredNativeSymbol rns,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode) {
            DLL.SymbolHandle func = findSymbolNode.execute(symbol, packageName, rns);
            if (func == DLL.SYMBOL_NOT_FOUND) {
                throw error(RError.Message.SYMBOL_NOT_IN_TABLE, symbol, "External2", packageName);
            }
            Object list = encodeArgumentPairList(args, symbol);
            REnvironment rho = REnvironment.frameToEnvironment(frame.materialize());
            return dispatch(frame, new NativeCallInfo(symbol, func, rns.getDllInfo()), new Object[]{CALL, getOp(), list, rho});
        }

        @Fallback
        protected Object fallback(Object f, @SuppressWarnings("unused") Object args, @SuppressWarnings("unused") Object packageName) {
            throw fallback(this, f);
        }
    }

    private abstract static class Dot extends LookupAdapter {
        @Child private InvokeCallNode callRFFINode = RFFIFactory.getCallRFFI().createInvokeCallNode();
        @Child private RExplicitCallNode explicitCall;

        protected Object dispatch(VirtualFrame frame, NativeCallInfo nativeCallInfo, Object[] args) {
            return callRFFINode.dispatch(frame, nativeCallInfo, args);
        }

        protected Object explicitCall(VirtualFrame frame, NativeCallInfo nativeCallInfo, RArgsValuesAndNames args) {
            return explicitCall(frame, nativeCallInfo.address, args);
        }

        protected Object explicitCall(VirtualFrame frame, RExternalPtr ptr, RArgsValuesAndNames args) {
            return explicitCall(frame, ptr.getAddr(), args);
        }

        protected Object explicitCall(VirtualFrame frame, SymbolHandle symbolHandle, RArgsValuesAndNames args) {
            if (explicitCall == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                explicitCall = insert(RExplicitCallNode.create());
            }
            RFunction function = (RFunction) symbolHandle.asTruffleObject();
            return explicitCall.call(frame, function, args);
        }

        protected boolean isRegisteredRFunction(NativeCallInfo nativeCallInfo) {
            return isRegisteredRFunction(nativeCallInfo.address);
        }

        protected boolean isRegisteredRFunction(RExternalPtr ptr) {
            DLL.SymbolHandle addr = ptr.getAddr();
            return !addr.isLong() && addr.asTruffleObject() instanceof RFunction;
        }

        protected static boolean isRegisteredRFunction(SymbolHandle handle) {
            return !handle.isLong() && handle.asTruffleObject() instanceof RFunction;
        }
    }

    @RBuiltin(name = ".External.graphics", kind = PRIMITIVE, parameterNames = {".NAME", "...", "PACKAGE"}, behavior = COMPLEX)
    public abstract static class DotExternalGraphics extends LookupAdapter {

        @Child private CallRFFI.InvokeCallNode callRFFINode = RFFIFactory.getCallRFFI().createInvokeCallNode();

        static {
            Casts.noCasts(DotExternalGraphics.class);
        }

        @Override
        @TruffleBoundary
        public RExternalBuiltinNode lookupBuiltin(RList f) {
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "1", guards = {"cached == f", "builtin != null"})
        protected Object doExternal(VirtualFrame frame, RList f, RArgsValuesAndNames args, RMissing packageName,
                        @Cached("f") RList cached,
                        @Cached("lookupBuiltin(f)") RExternalBuiltinNode builtin) {
            return builtin.call(frame, args);
        }

        @Specialization
        protected Object callNamedFunction(VirtualFrame frame, RList symbol, RArgsValuesAndNames args, @SuppressWarnings("unused") Object packageName,
                        @Cached("new()") ExtractNativeCallInfoNode extractSymbolInfo) {
            NativeCallInfo nativeCallInfo = extractSymbolInfo.execute(symbol);
            Object list = encodeArgumentPairList(args, nativeCallInfo.name);
            return callRFFINode.dispatch(frame, nativeCallInfo, new Object[]{list});
        }

        @Specialization
        protected Object callNamedFunction(VirtualFrame frame, String name, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode) {
            return callNamedFunctionWithPackage(frame, name, args, null, findSymbolNode);
        }

        @Specialization
        protected Object callNamedFunctionWithPackage(VirtualFrame frame, String name, RArgsValuesAndNames args, String packageName,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode) {
            DLL.RegisteredNativeSymbol rns = new DLL.RegisteredNativeSymbol(DLL.NativeSymbolType.External, null, null);
            DLL.SymbolHandle func = findSymbolNode.execute(name, packageName, rns);
            if (func == DLL.SYMBOL_NOT_FOUND) {
                throw error(RError.Message.C_SYMBOL_NOT_IN_TABLE, name);
            }
            Object list = encodeArgumentPairList(args, name);
            return callRFFINode.dispatch(frame, new NativeCallInfo(name, func, rns.getDllInfo()), new Object[]{list});
        }

        @Fallback
        protected Object fallback(Object f, @SuppressWarnings("unused") Object args, @SuppressWarnings("unused") Object packageName) {
            throw fallback(this, f);
        }
    }

    @RBuiltin(name = ".Call.graphics", kind = PRIMITIVE, parameterNames = {".NAME", "...", "PACKAGE"}, behavior = COMPLEX)
    public abstract static class DotCallGraphics extends LookupAdapter {

        @Child private CallRFFI.InvokeCallNode callRFFINode = RFFIFactory.getCallRFFI().createInvokeCallNode();

        static {
            Casts.noCasts(DotCallGraphics.class);
        }

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RMissing.instance, RArgsValuesAndNames.EMPTY, RMissing.instance};
        }

        @Override
        @TruffleBoundary
        public RExternalBuiltinNode lookupBuiltin(RList f) {
            if (RContext.getInstance().getOption(UseInternalGridGraphics)) {
                return FastRGridExternalLookup.lookupDotCallGraphics(lookupName(f));
            } else {
                return null;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "1", guards = {"cached == f", "builtin != null"})
        protected Object doExternal(VirtualFrame frame, RList f, RArgsValuesAndNames args, RMissing packageName,
                        @Cached("f") RList cached,
                        @Cached("lookupBuiltin(f)") RExternalBuiltinNode builtin) {
            return builtin.call(frame, args);
        }

        @Specialization
        protected Object callNamedFunction(VirtualFrame frame, RList symbol, RArgsValuesAndNames args, @SuppressWarnings("unused") Object packageName,
                        @Cached("new()") ExtractNativeCallInfoNode extractSymbolInfo) {
            NativeCallInfo nativeCallInfo = extractSymbolInfo.execute(symbol);
            return callRFFINode.dispatch(frame, nativeCallInfo, args.getArguments());
        }

        @Specialization
        protected Object callNamedFunction(VirtualFrame frame, String name, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode) {
            return callNamedFunctionWithPackage(frame, name, args, null, findSymbolNode);
        }

        @Specialization
        protected Object callNamedFunctionWithPackage(VirtualFrame frame, String name, RArgsValuesAndNames args, String packageName,
                        @Cached("create()") DLL.RFindSymbolNode findSymbolNode) {
            DLL.RegisteredNativeSymbol rns = new DLL.RegisteredNativeSymbol(DLL.NativeSymbolType.Call, null, null);
            DLL.SymbolHandle func = findSymbol(name, packageName, findSymbolNode, rns);
            return callRFFINode.dispatch(frame, new NativeCallInfo(name, func, rns.getDllInfo()), args.getArguments());
        }

        @TruffleBoundary
        private DLL.SymbolHandle findSymbol(String name, String packageName, DLL.RFindSymbolNode findSymbolNode, DLL.RegisteredNativeSymbol rns) {
            DLL.SymbolHandle func = findSymbolNode.execute(name, packageName, rns);
            if (func == DLL.SYMBOL_NOT_FOUND) {
                throw error(RError.Message.C_SYMBOL_NOT_IN_TABLE, name);
            }
            return func;
        }

        @Fallback
        protected Object dotCallFallback(Object fobj, @SuppressWarnings("unused") Object args, @SuppressWarnings("unused") Object packageName) {
            throw fallback(this, fobj);
        }
    }
}
