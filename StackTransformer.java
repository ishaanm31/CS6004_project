import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.BackwardFlowAnalysis;
import soot.toolkits.scalar.FlowSet;


public class StackTransformer extends BodyTransformer {
    public static HashMap<String, ArrayList<String>> answer= new HashMap<>();
    public static ArrayList<String> funcNames = new ArrayList<>();
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        // Construct CFG for the current method's body
        //System.out.println(body.getMethod().getDeclaringClass().toString()+": "+body.getMethod().getName());
        funcNames.add(body.getMethod().getDeclaringClass().toString()+":"+body.getMethod().getName());
        answer.put(body.getMethod().getDeclaringClass().toString()+":"+body.getMethod().getName(), new ArrayList<>());
        PatchingChain<Unit> units = body.getUnits();
        HashMap<Unit, PointsToGraph> ptgList = new HashMap<>();
        Queue<Unit> worklist = new LinkedList<>();
        HashMap<Unit, ArrayList<Unit>> successorList = new HashMap<>();
        HashMap<Unit, ArrayList<Unit>> predecessorList = new HashMap<>();
        ArrayList<Unit> returnList = new ArrayList<>();
        ArrayList<Unit> endPointList = new ArrayList<>();
        ArrayList<InvokeExpr> invokeList = new ArrayList<>();
        HashMap<Unit, Integer> dummyCounter = new HashMap<>();
        int totalDummy = 0;
        Queue<String> escapingObjects = new LinkedList<>();
        HashSet<String> escaping = new HashSet<>();
        HashSet<String> staticFields = new HashSet<>();
        HashMap<Unit, Integer> worklistCounter = new HashMap<>();
        HashSet<String> pseudoDummy = new HashSet<>();
        HashSet<String> currentMethodEscaping = new HashSet<>();
        HashSet<String> currentMethodStack = new HashSet<>();
        HashMap<String, HashMap<SootField, JimpleLocal>> objectLocalRef = new HashMap<>();

        final int maxCount = 2*units.size()*units.size();

        for (Unit u: units){
            PointsToGraph ptg = new PointsToGraph();
            ptgList.put(u, ptg);
            worklist.add(u);
            worklistCounter.put(u, 1);
            ArrayList<Unit> tempSuccList = new ArrayList<>();
            successorList.put(u, tempSuccList);
            ArrayList<Unit> tempPredList = new ArrayList<>();
            predecessorList.put(u, tempPredList);
            dummyCounter.put(u, 0);
        }

        for (Unit u: units){
            if (u instanceof JGotoStmt){
                JGotoStmt current = (JGotoStmt) u;
                successorList.get(current).add(current.getTarget());
                predecessorList.get(current.getTarget()).add(current);
            }
            else if (u instanceof JIfStmt){
                JIfStmt current = (JIfStmt) u;
                successorList.get(current).add(current.getTarget());
                predecessorList.get(current.getTarget()).add(current);
                successorList.get(current).add(units.getSuccOf(current));
                predecessorList.get(units.getSuccOf(current)).add(current);
            }
            else if (u instanceof JTableSwitchStmt){
                JTableSwitchStmt current = (JTableSwitchStmt) u;
                successorList.get(current).add(current.getDefaultTarget());
                predecessorList.get(current.getDefaultTarget()).add(current);
                List<Unit> targetList = current.getTargets();
                for (Unit k: targetList){
                    successorList.get(current).add(k);
                    predecessorList.get(k).add(current);
                }
            }
            else if (u instanceof JLookupSwitchStmt){
                JLookupSwitchStmt current = (JLookupSwitchStmt) u;
                successorList.get(current).add(current.getDefaultTarget());
                predecessorList.get(current.getDefaultTarget()).add(current);
                List<Unit> targetList = current.getTargets();
                for (Unit k: targetList){
                    successorList.get(current).add(k);
                    predecessorList.get(k).add(current);
                }
            }
            else{
                if (units.getSuccOf(u)!=null){
                    successorList.get(u).add(units.getSuccOf(u));
                    predecessorList.get(units.getSuccOf(u)).add(u);
                }
            }
        }

        // Iterate over each unit of CFG.
        // Shown how to get the line numbers if the unit is a "new" Statement.
        for (Unit u : units) {
            //System.out.println(u);
            if (u instanceof JReturnVoidStmt || u instanceof JReturnStmt){
                returnList.add(u);
            }
            if (successorList.get(u).size()==0){
                endPointList.add(u);
            }
            if (u instanceof JInvokeStmt){
                JInvokeStmt current = (JInvokeStmt) u;
                invokeList.add(current.getInvokeExpr());
            }
            if (u instanceof JAssignStmt){
                JAssignStmt current = (JAssignStmt) u;
                // System.out.println(current.getLeftOp());
                // System.out.println(current.getLeftOp().getClass());
                if (current.getRightOp() instanceof InvokeExpr){
                    //invokeList.add(current.getRightOp());
                    InvokeExpr temp = (InvokeExpr) current.getRightOp();
                    invokeList.add(temp);
                }
            }
        }

        while (!worklist.isEmpty()){
            Unit current = worklist.poll();
            PointsToGraph old = new PointsToGraph(ptgList.get(current));
            ArrayList<PointsToGraph> predecessorPtg = new ArrayList<>();
            for (Unit u: predecessorList.get(current)){
                predecessorPtg.add(ptgList.get(u));
            }
            ptgList.get(current).takeUnion(predecessorPtg);
            if (current instanceof JInvokeStmt){
                JInvokeStmt current_unit = (JInvokeStmt) current;
                InvokeExpr expr = current_unit.getInvokeExpr();
                for (Value v: expr.getArgs()){
                    String s = v.toString();
                    HashSet<String> temp = ptgList.get(current_unit).getReferenceVarAdjList(s);
                    for (String k: temp){
                        pseudoDummy.add(k);
                    }
                }
            }
            if (current instanceof JAssignStmt){
                JAssignStmt current_unit = (JAssignStmt) current;
                if (current_unit.getRightOp() instanceof InvokeExpr){
                    InvokeExpr expr = (InvokeExpr) current_unit.getRightOp();
                    for (Value v: expr.getArgs()){
                        String s = v.toString();
                        HashSet<String> temp = ptgList.get(current_unit).getReferenceVarAdjList(s);
                        for (String k: temp){
                            pseudoDummy.add(k);
                        }
                    }
                }
                if (current_unit.getLeftOp() instanceof JimpleLocal && current_unit.getRightOp() instanceof JNewExpr){
                    JimpleLocal leftExpr = (JimpleLocal) current_unit.getLeftOp();
                    JNewExpr rightExpr = (JNewExpr) current_unit.getRightOp();
                    SootClass currentClass = rightExpr.getBaseType().getSootClass();
                    Chain<SootField> classFields = currentClass.getFields();
                    SootField curr = classFields.getFirst();
                    objectLocalRef.put(current_unit.getJavaSourceStartLineNumber(), new HashMap<>());
                    while(curr!=null){
                        objectLocalRef.get(current_unit.getJavaSourceStartLineNumber()).put(curr, new JimpleLocal("local"+current_unit.getJavaSourceStartLineNumber()+curr.getName(), curr.getType()));
                        curr = classFields.getSuccOf(curr);
                    }
                    ptgList.get(current_unit).addRefEdge(leftExpr.getName(), ""+current_unit.getJavaSourceStartLineNumber());
                }
                else if (current_unit.getLeftOp() instanceof JimpleLocal && current_unit.getRightOp() instanceof JimpleLocal){
                    JimpleLocal leftExpr = (JimpleLocal) current_unit.getLeftOp();
                    JimpleLocal rightExpr = (JimpleLocal) current_unit.getRightOp();
                    if (ptgList.get(current_unit).refVarExists(rightExpr.getName())){
                        ptgList.get(current_unit).clearReferenceVarEdges(leftExpr.getName());
                        for (String objNodeName: ptgList.get(current_unit).getReferenceVarAdjList(rightExpr.getName())){
                            ptgList.get(current_unit).addRefEdge(leftExpr.getName(), objNodeName);
                        }
                    }
                }
                else if (current_unit.getLeftOp() instanceof JimpleLocal && current_unit.getRightOp() instanceof JInstanceFieldRef){
                    JimpleLocal leftExpr = (JimpleLocal) current_unit.getLeftOp();
                    JInstanceFieldRef rightExpr = (JInstanceFieldRef) current_unit.getRightOp();
                    String rightExprVarName = rightExpr.getBase().toString();
                    String rightExprField = rightExpr.getField().getName();
                    if (ptgList.get(current_unit).refVarExists(rightExprVarName)){
                        HashSet<String> objList = ptgList.get(current_unit).getReferenceVarAdjList(rightExprVarName);
                        HashSet<String> tempObjList = new HashSet<>();
                        for (String obj: objList){
                            tempObjList.add(obj);
                        }
                        boolean flag=true;
                        for (String obj: objList){
                            if ((!(obj.charAt(0)=='D' || pseudoDummy.contains(obj))) && ptgList.get(current_unit).getObjectNodeAdjList(obj, rightExprField)==null){
                                flag=false;
                                break;
                            }
                        }
                        if (flag){
                            for (String obj: tempObjList){
                                ptgList.get(current_unit).clearReferenceVarEdges(leftExpr.getName());
                                if (!(obj.charAt(0)=='D' || pseudoDummy.contains(obj))){
                                    HashSet<String> tempList = ptgList.get(current_unit).getObjectNodeAdjList(obj, rightExprField);
                                    for (String tempObj: tempList){
                                        ptgList.get(current_unit).addRefEdge(leftExpr.getName(), tempObj);
                                    }
                                }
                                else{
                                    HashSet<String> tempList = ptgList.get(current_unit).getObjectNodeAdjList(obj, rightExprField);
                                    if (tempList==null){
                                        ptgList.get(current_unit).addObjEdge(obj, "D"+"_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber(), rightExprField);
                                        dummyCounter.put(current_unit, dummyCounter.get(current_unit)+1);
                                        totalDummy+=1;
                                    }
                                    tempList = ptgList.get(current_unit).getObjectNodeAdjList(obj, rightExprField);
                                    for (String tempObj: tempList){
                                        ptgList.get(current_unit).addRefEdge(leftExpr.getName(), tempObj);
                                    }
                                }
                            }
                        }
                    }
                }
                else if (current_unit.getLeftOp() instanceof JInstanceFieldRef && current_unit.getRightOp() instanceof JimpleLocal){
                    JInstanceFieldRef leftExpr = (JInstanceFieldRef) current_unit.getLeftOp();
                    JimpleLocal rightExpr = (JimpleLocal) current_unit.getRightOp();
                    String leftExprVarName = leftExpr.getBase().toString();
                    String leftExprField = leftExpr.getField().getName();
                    if (ptgList.get(current_unit).refVarExists(rightExpr.getName()) && ptgList.get(current_unit).refVarExists(leftExprVarName)){
                        HashSet<String> objList = ptgList.get(current_unit).getReferenceVarAdjList(leftExprVarName);
                        for (String obj: objList){
                            for (String temp: ptgList.get(current_unit).getReferenceVarAdjList(rightExpr.getName())){
                                ptgList.get(current_unit).addObjEdge(obj, temp, leftExprField);
                            }
                        }
                    }
                }
                else if (current_unit.getLeftOp() instanceof JInstanceFieldRef && current_unit.getRightOp() instanceof InvokeExpr){
                    JInstanceFieldRef leftExpr = (JInstanceFieldRef) current_unit.getLeftOp();
                    String leftExprVarName = leftExpr.getBase().toString();
                    String leftExprField = leftExpr.getField().getName();
                    if (ptgList.get(current_unit).refVarExists(leftExprVarName)){
                        HashSet<String> objList = ptgList.get(current_unit).getReferenceVarAdjList(leftExprVarName);
                        for (String obj: objList){
                            ptgList.get(current_unit).addObjEdge(obj, "D_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber(), leftExprField);
                            escaping.add("D_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber());
                            dummyCounter.put(current_unit, dummyCounter.get(current_unit)+1);
                            totalDummy+=1;
                        }
                    }
                }
                else if (current_unit.getLeftOp() instanceof JimpleLocal && current_unit.getRightOp() instanceof InvokeExpr){
                    JimpleLocal leftExpr = (JimpleLocal) current_unit.getLeftOp();
                    ptgList.get(current_unit).clearReferenceVarEdges(leftExpr.getName());
                    ptgList.get(current_unit).addRefEdge(leftExpr.getName(), "D_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber());
                    escaping.add("D_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber());
                    dummyCounter.put(current_unit, dummyCounter.get(current_unit)+1);
                    totalDummy+=1;
                }
                else if (current_unit.getLeftOp() instanceof StaticFieldRef){
                    StaticFieldRef leftExpr = (StaticFieldRef) current_unit.getLeftOp();
                    JimpleLocal rightExpr = (JimpleLocal) current_unit.getRightOp();
                    if (ptgList.get(current_unit).refVarExists(rightExpr.getName())){
                        ptgList.get(current_unit).clearReferenceVarEdges("static_"+leftExpr.getField().toString());
                        staticFields.add("static_"+leftExpr.getField().toString());
                        for (String objNodeName: ptgList.get(current_unit).getReferenceVarAdjList(rightExpr.getName())){
                            ptgList.get(current_unit).addRefEdge("static_"+leftExpr.getField().toString(), objNodeName);
                        }
                    }
                }
                else if (current_unit.getRightOp() instanceof StaticFieldRef){
                    JimpleLocal leftExpr = (JimpleLocal) current_unit.getLeftOp();
                    StaticFieldRef rightExpr = (StaticFieldRef) current_unit.getRightOp();
                    ptgList.get(current_unit).clearReferenceVarEdges(leftExpr.getName());
                    staticFields.add("static_"+rightExpr.getField().toString());
                    if (!ptgList.get(current_unit).refVarExists("static_"+rightExpr.getField().toString())){
                        ptgList.get(current_unit).addRefEdge("static_"+rightExpr.getField().toString(), "D_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber());
                        dummyCounter.put(current_unit, dummyCounter.get(current_unit)+1);
                        totalDummy+=1;
                    }
                    for (String objNodeName: ptgList.get(current_unit).getReferenceVarAdjList("static_"+rightExpr.getField().toString())){
                        ptgList.get(current_unit).addRefEdge(leftExpr.getName(), objNodeName);
                    }
                }
                else if (current_unit.getLeftOp() instanceof JimpleLocal && current_unit.getRightOp() instanceof JNewArrayExpr){
                    JimpleLocal leftExpr = (JimpleLocal) current_unit.getLeftOp();
                    JNewArrayExpr rightExpr = (JNewArrayExpr) current_unit.getRightOp();
                    ptgList.get(current_unit).addRefEdge(leftExpr.getName(), ""+current_unit.getJavaSourceStartLineNumber());
                }
                else if (current_unit.getLeftOp() instanceof JArrayRef && current_unit.getRightOp() instanceof JimpleLocal){
                    JArrayRef leftExpr = (JArrayRef) current_unit.getLeftOp();
                    JimpleLocal rightExpr = (JimpleLocal) current_unit.getRightOp();
                    String leftExprVarName = leftExpr.getBase().toString();
                    if (ptgList.get(current_unit).refVarExists(rightExpr.getName()) && ptgList.get(current_unit).refVarExists(leftExprVarName)){
                        HashSet<String> objList = ptgList.get(current_unit).getReferenceVarAdjList(leftExprVarName);
                        for (String obj: objList){
                            for (String temp: ptgList.get(current_unit).getReferenceVarAdjList(rightExpr.getName())){
                                ptgList.get(current_unit).addObjEdge(obj, temp, "[]");
                            }
                        }
                    }
                }
                else if (current_unit.getLeftOp() instanceof JimpleLocal && current_unit.getRightOp() instanceof JArrayRef){
                    JimpleLocal leftExpr = (JimpleLocal) current_unit.getLeftOp();
                    JArrayRef rightExpr = (JArrayRef) current_unit.getRightOp();
                    String rightExprVarName = rightExpr.getBase().toString();
                    if (ptgList.get(current_unit).refVarExists(rightExprVarName)){
                        HashSet<String> objList = ptgList.get(current_unit).getReferenceVarAdjList(rightExprVarName);
                        boolean flag=true;
                        for (String obj: objList){
                            if (obj.charAt(0)!='D' && ptgList.get(current_unit).getObjectNodeAdjList(obj, "[]")==null){
                                flag=false;
                                break;
                            }
                        }
                        if (flag){
                            for (String obj: objList){
                                ptgList.get(current_unit).clearReferenceVarEdges(leftExpr.getName());
                                if (obj.charAt(0)!='D'){
                                    HashSet<String> tempList = ptgList.get(current_unit).getObjectNodeAdjList(obj, "[]");
                                    for (String tempObj: tempList){
                                        ptgList.get(current_unit).addRefEdge(leftExpr.getName(), tempObj);
                                    }
                                }
                                else{
                                    HashSet<String> tempList = ptgList.get(current_unit).getObjectNodeAdjList(obj, "[]");
                                    if (tempList==null){
                                        ptgList.get(current_unit).addObjEdge(obj, "D"+"_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber(), "[]");
                                        dummyCounter.put(current_unit, dummyCounter.get(current_unit)+1);
                                        totalDummy+=1;
                                    }
                                    tempList = ptgList.get(current_unit).getObjectNodeAdjList(obj, "[]");
                                    for (String tempObj: tempList){
                                        ptgList.get(current_unit).addRefEdge(leftExpr.getName(), tempObj);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else if (current instanceof JIdentityStmt){
                JIdentityStmt current_unit = (JIdentityStmt) current;
                if (current_unit.getLeftOp() instanceof JimpleLocal && current_unit.getRightOp() instanceof ParameterRef){
                    JimpleLocal leftExpr = (JimpleLocal) current_unit.getLeftOp();
                    ParameterRef rightExpr = (ParameterRef) current_unit.getRightOp();
                    escaping.add("D"+"_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber());
                    ptgList.get(current_unit).addRefEdge(leftExpr.getName(), "D"+"_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber());
                    dummyCounter.put(current_unit, dummyCounter.get(current_unit)+1);
                    totalDummy+=1;
                }
                else if (current_unit.getLeftOp() instanceof JimpleLocal && current_unit.getRightOp() instanceof ThisRef){
                    JimpleLocal leftExpr = (JimpleLocal) current_unit.getLeftOp();
                    escaping.add("D"+"_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber());
                    ptgList.get(current_unit).addRefEdge(leftExpr.getName(), "D"+"_"+dummyCounter.get(current_unit)+"_"+current_unit.getJavaSourceStartLineNumber());
                    dummyCounter.put(current_unit, dummyCounter.get(current_unit)+1);
                    totalDummy+=1;
                }
            }
            if (!old.areEqual(ptgList.get(current))){
                for (Unit u: successorList.get(current)){
                    if (worklistCounter.get(u)<maxCount){
                        worklist.add(u);
                        worklistCounter.put(u, worklistCounter.get(u)+1);
                    }
                }
            }
        }
        PointsToGraph finalPtg = new PointsToGraph();
        ArrayList<PointsToGraph> endPointPtgList = new ArrayList<>();
        for (Unit u: endPointList){
            endPointPtgList.add(ptgList.get(u));
        }
        finalPtg.takeUnion(endPointPtgList);
        for (Unit u: returnList){
            if (u instanceof JReturnStmt){
                JReturnStmt current = (JReturnStmt) u;
                HashSet<String> temp = finalPtg.getReferenceVarAdjList(current.getOp().toString());
                for (String s: temp){
                    escaping.add(s);
                }
            }
        }
        for (InvokeExpr e: invokeList){
            if (e instanceof JVirtualInvokeExpr){
                JVirtualInvokeExpr c = (JVirtualInvokeExpr) e;
                String s = c.getBase().toString();
                HashSet<String> temp = finalPtg.getReferenceVarAdjList(s);
                for (String t: temp){
                    escaping.add(t);
                }
            }
            for (Value v: e.getArgs()){
                String s = v.toString();
                HashSet<String> temp = finalPtg.getReferenceVarAdjList(s);
                for (String t: temp){
                    escaping.add(t);
                }
            }
        }
        for (String s: staticFields){
            HashSet<String> temp = finalPtg.getReferenceVarAdjList(s);
            for (String t: temp){
                escaping.add(t);
            }
        }
        for (String s: escaping){
            escapingObjects.add(s);
        }
        HashMap<String, Integer> visited = new HashMap<>();
        for (String s: finalPtg.getObjectNodeNameList()){
            visited.put(s,0);
        }
        for (String s: escaping){
            visited.put(s, 1);
        }
        while (!escapingObjects.isEmpty()){
            String current = escapingObjects.poll();
            if (current.charAt(0)!='D'){
                answer.get(body.getMethod().getDeclaringClass().toString()+":"+body.getMethod().getName()).add(current);
                currentMethodEscaping.add(current);
            }
            HashMap<String, HashSet<String>> temp = finalPtg.getObjectNodeFull(current);
            for (String f: temp.keySet()){
                HashSet<String> t1 = temp.get(f);
                for (String o: t1){
                    if (visited.get(o)==0){
                        escapingObjects.add(o);
                        visited.put(o, 1);
                    }
                }
            }
        }
        Collections.sort(answer.get(body.getMethod().getDeclaringClass().toString()+":"+body.getMethod().getName()));

        getStack(ptgList, currentMethodStack, currentMethodEscaping);

        HashMap<Unit, ArrayList<Unit>> newLocalRef = new HashMap<>();
        HashSet<Unit> removeNew = new HashSet<>();
        HashSet<Unit> removeAssign = new HashSet<>();
        
        for (Unit u: units){
            if (u instanceof JAssignStmt){
                JAssignStmt tempu = (JAssignStmt) u;
                if (tempu.getLeftOp() instanceof JimpleLocal && tempu.getRightOp() instanceof JNewExpr){
                    if (currentMethodStack.contains(u.getJavaSourceStartLineNumber())){
                        newLocalRef.put(u, new ArrayList<>());
                        JimpleLocal oldReference = (JimpleLocal) tempu.getLeftOp();
                        newLocalRef.get(u).add(new JAssignStmt(oldReference, NullConstant.v()));
                        removeNew.add(u);
                        for (SootField f: objectLocalRef.get(u.getJavaSourceStartLineNumber().toString()).keySet()){
                            JimpleLocal tempLocal = objectLocalRef.get(u.getJavaSourceStartLineNumber().toString()).get(f);
                            newLocalRef.get(u).add(new JAssignStmt(tempLocal, NullConstant.v()));
                        }
                    }
                }
            }
        }
        for (Unit u: newLocalRef.keySet()){
            units.insertAfter(newLocalRef.get(u),u);
        }
        for (Unit u: removeNew){
            units.remove(u);
        }
        for (Unit u: units){
            if (u instanceof JAssignStmt){
                JAssignStmt current = (JAssignStmt) u;
                if (current.getRightOp() instanceof JimpleLocal){
                    // remove x = y type statements where y is reference to object that points to stack-allocated object
                    JimpleLocal rightExpr = (JimpleLocal) current.getRightOp();
                    if (ptgList.get(current).refVarExists(rightExpr.getName())){
                        if (ptgList.get(current).getReferenceVarAdjList(rightExpr.getName()).size()==1){
                            Boolean flag = false;
                            for (String objNodeName: ptgList.get(current).getReferenceVarAdjList(rightExpr.getName())){
                                if (currentMethodStack.contains(objNodeName)){
                                    flag = true;
                                }
                            }
                            if (flag){
                                removeAssign.add(current);
                            }
                        }
                    }
                }
                else if (current.getRightOp() instanceof JInstanceFieldRef){
                    JInstanceFieldRef rightExpr = (JInstanceFieldRef) current.getRightOp();
                    String rightExprVarName = rightExpr.getBase().toString();
                    SootField rightExprField = rightExpr.getField();
                    if (ptgList.get(current).refVarExists(rightExprVarName)){
                        HashSet<String> objList = ptgList.get(current).getReferenceVarAdjList(rightExprVarName);
                        if (objList.size()==1){
                            Boolean flag = false;
                            for (String obj: objList){
                                if (currentMethodStack.contains(obj)){
                                    flag = true;
                                }
                            }
                            if (flag){
                                // x = y.f is converted to x = y_f if y points to stack allocated object
                                for (String obj: objList){
                                    current.setRightOp(objectLocalRef.get(obj).get(rightExprField));
                                }
                            }
                        }
                        // x = y.f is removed if y.f points to stack allocated object
                        for (String obj: objList){
                            HashSet<String> tempList = ptgList.get(current).getObjectNodeAdjList(obj, rightExprField.getName());
                            for (String obj2: tempList){
                                if (currentMethodStack.contains(obj2)){
                                    removeAssign.add(current);
                                }
                                break;
                            }
                            break;
                        }
                    }
                }
                else if (current.getLefttOp() instanceof JInstanceFieldRef){
                    JInstanceFieldRef leftExpr = (JInstanceFieldRef) current.getLeftOp();
                    String leftExprVarName = leftExpr.getBase().toString();
                    SootField leftExprField = leftExpr.getField();
                    if (ptgList.get(current).refVarExists(leftExprVarName)){
                        HashSet<String> objList = ptgList.get(current).getReferenceVarAdjList(leftExprVarName);
                        if (objList.size()==1){
                            Boolean flag = false;
                            for (String obj: objList){
                                if (currentMethodStack.contains(obj)){
                                    flag = true;
                                }
                            }
                            if (flag){
                                // y.f = x is converted to y_f = x if y points to stack allocated object
                                for (String obj: objList){
                                    current.setLeftOp(objectLocalRef.get(obj).get(leftExprField));
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Unit u: removeAssign){
            units.remove(u);
        }
    }
    private void getStack(HashMap<Unit, PointsToGraph> ptgList, HashSet<String> currentMethodStack, HashSet<String> currentMethodEscaping){

    }
}
