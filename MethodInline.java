import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.LiveLocals;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.BackwardFlowAnalysis;
import soot.toolkits.scalar.FlowSet;
/* Written by: Ishaan Manhar, Suven Jagtiani
 * For any queries mail ishaanmanhar2002@gmail.com
 * Purpose: Tries to statically change dynamic virtualinvokes to statically determined monomorphic staticinvoke
 * Methodology: 
 * Step 1: Create a Call Graph 
 * Step 2: If a call site outgoing edges are more than MAX_BRANCH then leave it
 * Step 3: Otherwise convert it to static invokes
 */

public class MethodInline extends SceneTransformer {
    static final int MAX_BRANCH = 4;
    static CallGraph cg;
    static PointsToAnalysis PTG;
    static Hierarchy ClassHierarchy;
    static Integer k=0;
    @Override
    synchronized protected void internalTransform(String arg0, Map<String, String> arg1) {
        Set<SootMethod> AllMethods = new HashSet <>();
        SootMethod mainMethod = Scene.v().getMainMethod();
        //getlistofMethods(mainMethod, AllMethods); //DFS
        Transform(mainMethod);
        // for(SootMethod method: AllMethods){
        //     if (shouldBeInlined(method)) {
        //         Transform(method);
        //     }
        // }
    }
    protected void Transform(SootMethod method){
        Body body = method.getActiveBody();

        // Inline the method
        Body newBody = Jimple.v().newBody(method);

        // Copy instructions from the original method body to the inline body
        UnitPatchingChain Units= body.getUnits();
        for (Unit unit : Units) {
            //System.out.println(unit);
            if(unit instanceof JInvokeStmt){
                JInvokeStmt stmt= (JInvokeStmt) unit;
                InvokeExpr expr= stmt.getInvokeExpr();
                if(expr instanceof JVirtualInvokeExpr){
                    if(true) PerformInline(newBody.getUnits(),stmt, Units.getSuccOf(stmt));
                }
            }
            else newBody.getUnits().add(unit);
        }
        for (Unit unit : newBody.getUnits()) {
            System.out.println(unit);
        }
        //method.setActiveBody(newBody);
    }
    private void PerformInline(UnitPatchingChain CallerUnits , JInvokeStmt callsite, Unit NextStmt){
        JVirtualInvokeExpr expr= (JVirtualInvokeExpr)callsite.getInvokeExpr();
        SootMethod method= expr.getMethod();
        Value Reciever= expr.getBase();
        List<Value> Args= expr.getArgs();
        Body CalleeBody= method.getActiveBody();
        UnitPatchingChain Units= CalleeBody.getUnits();
        Unit newU;
        for(Unit u: Units){
            if(u instanceof DefinitionStmt) ChangeLocalName((DefinitionStmt) u);
            if(u instanceof IdentityStmt){
                IdentityStmt stmt = (IdentityStmt)u; 
                Value lhs= stmt.getLeftOp();
                IdentityRef rhs= (IdentityRef)stmt.getRightOp();
                if(rhs instanceof ThisRef) newU = Jimple.v().newAssignStmt(lhs,Reciever);
                else {
                    int index= ((ParameterRef)rhs).getIndex() ;
                    newU = Jimple.v().newAssignStmt(lhs,Args.get(index));
                }
                CallerUnits.add(newU);
            }
            else if((u instanceof JReturnStmt) | (u instanceof  ReturnVoidStmt)){
                System.out.println("came here");
                newU= Jimple.v().newGotoStmt(NextStmt);
                CallerUnits.add(newU);
            }
            else{
                CallerUnits.add(u);
            }
        }
    }
    private void ChangeLocalName(DefinitionStmt u){
        Value lhs= u.getLeftOp();
        if(lhs instanceof Local){
            Local local= (Local) lhs;
            String name= local.getName();
            if(!name.contains("inline")){
                local.setName(name+"inline");
            }
        }
    }
    private static boolean shouldBeInlined(SootMethod method) {
        // Add your logic to determine if a method should be inlined
        // For example, based on method size, frequency of calls, etc.
        return true;
    }

    public static void getlistofMethods(SootMethod method, Set<SootMethod> reachableMethods) {
        // Avoid revisiting methods
        if (reachableMethods.contains(method)) {
            return;
        }
        // Add the method to the reachable set
        reachableMethods.add(method);

        // Iterate over the edges originating from this method
        Iterator<Edge> edges = cg.edgesOutOf(method);
        while (edges.hasNext()) {
            Edge edge = edges.next();
            SootMethod targetMethod = edge.tgt();
            // Recursively explore callee methods
            if (!targetMethod.isJavaLibraryMethod()) {
                getlistofMethods(targetMethod, reachableMethods);
            }
        }
    }
}
