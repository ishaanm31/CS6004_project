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

public class MonomorphicTransformer extends SceneTransformer {
    static final int MAX_BRANCH = 4;
    static CallGraph cg;
    static PointsToAnalysis PTG;
    static Hierarchy ClassHierarchy;
    @Override
    protected void internalTransform(String arg0, Map<String, String> arg1) {
        //Creating the call graph
        cg = Scene.v().getCallGraph();
        //Creating point to graph 
        PTG=Scene.v().getPointsToAnalysis();
        //Class Heirarchy useful to decide the sequence of if else ladder
        ClassHierarchy=Scene.v().getActiveHierarchy();
        //Getting all methods to be optimized in one Set
        Set<SootMethod> AllMethods = new HashSet <>();
        SootMethod mainMethod = Scene.v().getMainMethod();
        getlistofMethods(mainMethod, AllMethods); //DFS

        for(SootMethod Method: AllMethods){
            Transform(Method);            //This will individually transform each method 
        }
    }
    private void Transform(SootMethod Method){
        //Getting unit chain of the method
        Body body=Method.getActiveBody();
        PatchingChain<Unit> units= body.getUnits();

        for(Unit u: units){
            if(u instanceof JAssignStmt){
                JAssignStmt stmt= (JAssignStmt)u;
                Value rhs= stmt.getRightOp();
                if(rhs instanceof  JVirtualInvokeExpr){
                    JVirtualInvokeExpr rhs_vie= (JVirtualInvokeExpr)rhs;
                    //This is a virutalinvoke callsit which we wish to convert to multiple statics
                    Iterator<Edge> OutEdges= edgesOutOf(u);
                    List<Edge> list= new ArrayList<>();
                    Map<SootClass,BranchBox> Branches= new HashMap<>();
                    Set<BranchBox> S= new HashSet<>();
                    int BranchSize=0;
                    while(OutEdges.hasNext()){
                        if(MAX_BRANCH==BranchSize++) break;
                        Edge e = OutEdges.next();
                        SootMethod callee=(SootMethod)e.getTgt(); 
                        //z= obj instanceof Class
                        JimpleLocal z= new JimpleLocal("instanceofRes", IntType.v()) ;
                        JAssignStmt InstanceofStmt = new JAssignStmt(z, new JInstanceOfExpr( rhs_vie.getBase(),callee.getDeclaringClass().getType()));
                        JAssignStmt AssignmentStmt = new JAssignStmt(stmt.getLeftOp(),
                                                            new JVirtualInvokeExpr(rhs_vie.getBase(),callee.makeRef(), rhs_vie.getArgs()));
                        
                        BranchBox b= new BranchBox(callee.getDeclaringClass(),e,InstanceofStmt,AssignmentStmt);
                        Branches.put(callee.getDeclaringClass(),b);
                        S.add(b);
                    }
                    //If true means the branches are too many we are better off using a polymorphic call instead of a PIC
                    if(BranchSize==1+MAX_BRANCH) continue;

                    //Creating children relationship in branchbox taken from class heirarchy
                    for(BranchBox bb: S){
                        List<SootClass> l=getDirectSubclassesOf(bb.Klass) ;
                        for(SootClass c:l){
                            if(Branches.containsKey(c)) bb.addChild(Branches.get(c));
                        }
                    }

                    //Now lets create the sequence of branching 
                    List<BranchBox> BranchSequence = new ArrayList<>();
                    BranchBox.DfsMarker.empty();
                    for(BranchBox c: S) c.DFS(BranchSequence);

                    //Now its finally time to add our new branches to the body of our code

                    Unit StmtBefore= units.getPredOf(u);
                    Unit StmtAfter= units.getSuccof(u);
                    Unit IfTarget;
                    for(int i=0;i<BranchSequence.size();i++){
                        units.insertAfter(BranchSequence.get(i).InstanceofStmt,StmtBefore);
                        if(i==BranchSequence.size()-1)     IfTarget=StmtAfter;
                        else IfTarget= BranchSequence.get(i+1).InstanceofStmt;
                        Unit IfStmt = new JIfStmt(new JCmpExpr(BranchSequence.get(i).AssignmentStmt.getLeftOp(), new IntConstant(0)), IfTarget);
                        units.insertAfter(IfStmt,BranchSequence.get(i).InstanceofStmt);
                        units.insertAfter(BranchSequence.get(i).AssignmentStmt,IfStmt);
                        Unit GoTo=new JGotoStmt(StmtAfter);
                        units.insertAfter(GoTo,BranchSequence.get(i).AssignmentStmt);  
                        StmtBefore= GoTo;
                    }
                    units.insertAfter(StmtAfter,StmtBefore);
                    //Now our part is done of making the PIC
                }
            }
        }
    }
    private static void getlistofMethods(SootMethod method, Set<SootMethod> reachableMethods) {
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
