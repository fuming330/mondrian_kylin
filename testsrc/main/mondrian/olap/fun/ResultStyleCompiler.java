/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2007-2007 Julian Hyde and others
//
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.olap.fun;

import mondrian.olap.*;
import mondrian.olap.type.Type;
import mondrian.calc.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/** 
 * The <code>ResultStyleCompiler</code> can be used to assure that
 * the use of the container ResultStyle: ITERABLE, LIST and MUTABLE_LIST;
 * can be requested by any Calc. This ExpCompiler injects into the
 * Exp hierarchy a special Calc, the MultiCalc, that evaluates
 * its three child Calc's (one for ITERABLE, LIST and MUTABLE_LIST)
 * and compares the lists returned to make sure that they are the
 * same. This comparison can only be done when the Member evaluation 
 * stage of query evaluation is begin done the last time.
 * [Think about it - how can you tell when the evaluation is happening
 * for the last time.] Evaluation is called from the RolapResult's
 * constructor calling the method executeAxis. This happens one or
 * more times in the while-loop. These evaluations may not be complete;
 * you can not necessarily compare results. Then, evaluation occurs
 * just below the while-loop, again calling executeAxis. In this
 * case the evaluation is complete. The trick is to llok a the
 * stack and when one changes the line number from which one is
 * being called, then one knows one is being called by the second
 * executeAxis call in the RolapResult constructor.
 * 
 * @author <a>Richard M. Emberson</a>
 * @since Feb 10 2007
 * @version $Id$
 */
public class ResultStyleCompiler implements ExpCompiler {
    static {
        // This is here so that folks can see that this compiler is
        // being used. It should be removed in the future.
        System.out.println("ResultStyleCompiler being used");
    }

    /** 
     * Calc with three child Calcs, one for ITERABLE, LIST and MUTABLE_LIST,
     * which are evaluated during the normal evaluation process.
     */
    static class MultiCalc implements Calc {
        static int counter = 0;
        Calc calcIter;
        Calc calcList;
        Calc calcMList;
        boolean onlyMutableList;
        int lineNumber;

        int cnt;
        MultiCalc(Calc calcIter, Calc calcList, Calc calcMList, 
                            boolean onlyMutableList) {
            this.calcIter = calcIter;
            this.calcList = calcList;
            this.calcMList = calcMList;
            this.onlyMutableList = onlyMutableList;
            this.lineNumber = -1;
            this.cnt = counter++;
        }
        
        /** 
         * Return true if this is a final evaluation; the one that
         * takes place after the while-loop in the RolapResult 
         * constructor.
         * 
         * @return 
         */
        protected boolean finalEval() {
            StackTraceElement[] stEls = new Throwable().getStackTrace();
            for (int i = stEls.length-1; i > 0; i--) {
                StackTraceElement stEl = stEls[i];
                if (stEl.getClassName().equals("mondrian.rolap.RolapResult")) {
                    int ln = stEl.getLineNumber();
//System.out.println("MultiCalc.finalEval: ln="+ln + ", cnt="+cnt);
                    if (this.lineNumber == -1) {
                        this.lineNumber = ln;
                        return false;
                    } else {
                        return (this.lineNumber != ln);
                    }
                }
            }
            // should never happend
            System.out.println("MultiCalc.finalEval: MISS");
            return false;
        }
        public Object evaluate(Evaluator evaluator) {
            // We have to make copies of the Evaluator because of
            // the single test: NonEmptyTest.testVCNativeCJWithTopPercent
            Evaluator eval1 = evaluator.push();
            Evaluator eval2 = evaluator.push();
            Object valueIter = calcIter.evaluate(evaluator);
            Object valueList = calcList.evaluate(eval1);
            Object valueMList = calcMList.evaluate(eval2);

            if (finalEval()) {
/*
System.out.println("MultiCalc.evaluator: valueIter.size="+((List)valueIter).size());
System.out.println("MultiCalc.evaluator: valueList.size="+((List)valueList).size());
System.out.println("MultiCalc.evaluator: valueMList.size="+((List)valueMList).size());
*/

                if (! compare(valueIter, valueList)) {
                    throw new RuntimeException("MultiCalc.evaluator: ERROR Iter-List");
                }
                if (! compare(valueIter, valueMList)) {
                    throw new RuntimeException("MultiCalc.evaluator: ERROR Iter-MList");
                }
/*
                if (! compare(valueMList, valueList)) {
System.out.println("MultiCalc.evaluator: lists NOT EQUALS cnt="+cnt);
                } else {
System.out.println("MultiCalc.evaluator: lists EQUALS cnt="+cnt);
                }
*/

            }

            return (onlyMutableList) ? valueMList : valueIter;
        }
        public boolean dependsOn(Dimension dimension) {
            return calcIter.dependsOn(dimension);
        }
        public Type getType() {
            return calcIter.getType();
        }
        public void accept(CalcWriter calcWriter) {
            calcIter.accept(calcWriter);
        }
        public ExpCompiler.ResultStyle getResultStyle() {
            return calcIter.getResultStyle();
        }
        protected boolean compare(Object v1, Object v2) {
            if (v1 == null) {
                return (v2 == null);
            } else if (v2 == null) {
                return false;
            } else {
                if (! (v1 instanceof Iterable)) {
                    return false;
                }
                if (! (v2 instanceof Iterable)) {
                    return false;
                }
                Iterable iter1 = (Iterable) v1;
                Iterable iter2 = (Iterable) v2;
                Iterator it1 = iter1.iterator();
                Iterator it2 = iter2.iterator();
                while (it1.hasNext()) {
                    if (! it2.hasNext()) {
                        // too few
                        return false;
                    }
                    Object o1 = it1.next();
                    Object o2 = it2.next();
                    if (o1 == null) {
                        if (o2 != null) {
                            return false;
                        }
                    } else if (o2 == null) {
                        return false;
                    } else {
                        if (o1 instanceof Member) {
                            if (! o1.equals(o2)) {
                                return false;
                            }
                        } else {
                            Member[] ma1 = (Member[]) o1;
                            Member[] ma2 = (Member[]) o2;
                            if (! Arrays.equals(ma1, ma2)) {
/*
System.out.println("MultiCalc.compare: not equals");
System.out.println("MultiCalc.compare: o1="+o1);
print(ma1);
System.out.println("MultiCalc.compare: o2="+o2);
print(ma2);
*/
                                return false;
                            }
                        }
                    }
                }
                if (it2.hasNext()) {
                    // too many
                    return false;
                }

                return true;
            }
        }
        protected void print(Member[] ma) {
            StringBuffer buf = new StringBuffer(100);
            if (ma == null) {
                buf.append("null");
            } else {
                for (int i = 0; i < ma.length; i++) {
                    Member m = ma[i];
                    if (i > 0) {
                        buf.append(',');
                    }
                    buf.append(m.getUniqueName());
                    buf.append(' ');
                }
            }
            System.out.println(buf.toString());
        }
    }

    protected ExpCompiler compiler;

    /** 
     * Constructor which uses the ExpCompiler.Factory to get the
     * default ExpCompiler as an instance variable - ResultStyleCompiler
     * is a wrapper.
     * 
     * @param evaluator 
     * @param validator 
     * @param resultStyles 
     */
    public ResultStyleCompiler(Evaluator evaluator, Validator validator,
            ResultStyle[] resultStyles) {
        // pop and then push class name
        Object context = ExpCompiler.Factory.getFactory().removeContext();
        try {
            this.compiler = ExpCompiler.Factory.getExpCompiler(
                                evaluator, validator, resultStyles);
        } finally {
            // reset ExpCompiler.Factory test context
            ExpCompiler.Factory.getFactory().restoreContext(context);
        }
    }

    public Evaluator getEvaluator() {
        return compiler.getEvaluator();
    }
    public Validator getValidator() {
        return compiler.getValidator();
    }

    public Calc compile(Exp exp) {
        ResultStyle[] resultStyles = getAcceptableResultStyles();
        return compile(exp, resultStyles);
    }

    public Calc compile(Exp exp, ResultStyle[] resultStyles) {
        //return compiler.compile(exp, resultStyles);
        // This applies only to the ITERABE, LIST and MUTABLE_LIST
        // ResultStyles. For each we compile and save the Calc
        // in a Multi-Calc, then during evaluation, each of the
        // calcs are evaluated and results compared.
        // If the request is for a MUTABLE_LIST, then that result HAS to
        // be returned to caller.
        if (resultStyles.length > 0) {
            boolean foundIterable = false;
            boolean foundList = false;
            boolean foundMutableList = false;
            for (int i = 0; i < resultStyles.length; i++) {
                ResultStyle resultStyle = resultStyles[i];
                if (resultStyle == ResultStyle.LIST) {
                    foundList = true;
                } else if (resultStyle == ResultStyle.MUTABLE_LIST) {
                    foundMutableList = true;
                } else if (resultStyle == ResultStyle.ITERABLE) {
                    foundIterable = true;
                }
            }
            // found at least one of the container Calcs
            if (foundIterable || foundList || foundMutableList) {
                Calc calcIter = compiler.compile(exp, 
                                new ResultStyle[] { ResultStyle.ITERABLE });
                Calc calcList = compiler.compile(exp, 
                                new ResultStyle[] { ResultStyle.LIST });
                Calc calcMList = compiler.compile(exp, 
                                new ResultStyle[] { ResultStyle.MUTABLE_LIST });
                return new MultiCalc(calcIter, calcList, calcMList, 
                                // only a mutable list was requested
                                // so that is the one that MUST be returned
                                ! (foundList || foundIterable));
            } else {
                return compiler.compile(exp);
            }
        } else {
            return compiler.compile(exp);
        }
    }

    public MemberCalc compileMember(Exp exp) {
        return compiler.compileMember(exp);
    }

    public LevelCalc compileLevel(Exp exp) {
        return compiler.compileLevel(exp);
    }

    public DimensionCalc compileDimension(Exp exp) {
        return compiler.compileDimension(exp);
    }

    public HierarchyCalc compileHierarchy(Exp exp) {
        return compiler.compileHierarchy(exp);
    }

    public IntegerCalc compileInteger(Exp exp) {
        return compiler.compileInteger(exp);
    }

    public StringCalc compileString(Exp exp) {
        return compiler.compileString(exp);
    }

    public ListCalc compileList(Exp exp) {
        return compiler.compileList(exp);
    }

    public ListCalc compileList(Exp exp, boolean mutable) {
        return compiler.compileList(exp, mutable);
    }
    public IterCalc compileIter(Exp exp) {
        return compiler.compileIter(exp);
    }

    public BooleanCalc compileBoolean(Exp exp) {
        return compiler.compileBoolean(exp);
    }

    public DoubleCalc compileDouble(Exp exp) {
        return compiler.compileDouble(exp);
    }

    public TupleCalc compileTuple(Exp exp) {
        return compiler.compileTuple(exp);
    }

    public Calc compileScalar(Exp exp, boolean convert) {
        return compiler.compileScalar(exp, convert);
    }

    public ParameterSlot registerParameter(Parameter parameter) {
        return compiler.registerParameter(parameter);
    }

    public ResultStyle[] getAcceptableResultStyles() {
        return compiler.getAcceptableResultStyles();
    }
}

// End ResultStyleCompiler.java