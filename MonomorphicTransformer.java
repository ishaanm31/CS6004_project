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
    static Integer k=0;
    @Override
    synchronized protected void internalTransform(String arg0, Map<String, String> arg1) {
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
        System.out.println("Method: "+ Method.toString());
        //Getting unit chain of the method
        Body body=Method.getActiveBody();   //Body of the Method
        ExceptionalUnitGraph cfg= new ExceptionalUnitGraph(body); //Control Flow Graph
        PatchingChain<Unit> units= body.getUnits();  //Units of the body
        Set<JAssignStmt> AssignSet= new HashSet<>(); //Contains all the x=y.foo() units
        Set<JInvokeStmt> InvokeSet= new HashSet<>(); //Contains all z.bar() units

        //Filling AssignSet and Invoke Set
        for(Unit u: units){
            if(u instanceof JAssignStmt){
                JAssignStmt stmt= (JAssignStmt)u;
                Value rhs= stmt.getRightOp();
                if(rhs instanceof VirtualInvokeExpr) AssignSet.add(stmt);
            }
            if(u instanceof JInvokeStmt){
                JInvokeStmt stmt = (JInvokeStmt) u;
                Value expr = stmt.getInvokeExpr();
                if(expr instanceof JVirtualInvokeExpr) InvokeSet.add(stmt);
            }
        }
        for(JAssignStmt stmt: AssignSet){
            Value rhs= stmt.getRightOp();
            System.out.println("Transforming asssign statement: "+ stmt.toString());
            JVirtualInvokeExpr RhsVie= (JVirtualInvokeExpr)rhs;
            //This is a virutalinvoke callsite which we wish to convert to multiple statically known invokes
            Iterator<Edge> OutEdges= cg.edgesOutOf(stmt); //Edges represent the possible invokation
            List<Edge> list= new ArrayList<>();           
            Map<SootClass,BranchBox> Branches= new HashMap<>();  // one possible type of the object-> BranchBox
            Set<BranchBox> S= new HashSet<>();      //All Branchboxes
            int BranchSize=0;
            System.out.println("OutEdges : ");
            while(OutEdges.hasNext()){
                if(MAX_BRANCH==BranchSize++) break;
                Edge e = OutEdges.next();
                System.out.println(e);
                SootMethod callee=(SootMethod)e.getTgt(); 
                //z= obj instanceof Class
                Local z= Jimple.v().newLocal("instanceofRes"+k.toString(), IntType.v()) ;
                Local RecieverCasted= Jimple.v().newLocal("RecieverCast"+k.toString(),callee.getDeclaringClass().getType());
                k++;
                AssignStmt InstanceofStmt = Jimple.v().newAssignStmt(z, Jimple.v().newInstanceOfExpr( RhsVie.getBase(),callee.getDeclaringClass().getType()));
                AssignStmt CastStmt = Jimple.v().newAssignStmt(RecieverCasted, Jimple.v().newCastExpr(RhsVie.getBase(),callee.getDeclaringClass().getType()));
                VirtualInvokeExpr VIE= Jimple.v().newVirtualInvokeExpr(RecieverCasted,callee.makeRef(), RhsVie.getArgs());
                AssignStmt AssignmentStmt = Jimple.v().newAssignStmt(stmt.getLeftOp(), VIE);                                                                                 
                BranchBox b= new BranchBox(callee.getDeclaringClass(),e,InstanceofStmt,AssignmentStmt,CastStmt);
                Branches.put(callee.getDeclaringClass(),b);
                S.add(b);                
            }
            //If true means the branches are too many we are better off using a polymorphic call instead of a PIC
            if(BranchSize==1+MAX_BRANCH) continue;

            //Creating children relationship in branchbox taken from class heirarchy
            for(BranchBox bb: S){
                List<SootClass> l= ClassHierarchy.getDirectSubclassesOf(bb.Klass) ;
                for(SootClass c:l){
                    if(Branches.containsKey(c)) bb.addChild(Branches.get(c));
                }
            }

            //Now lets create the sequence of branching 
            List<BranchBox> BranchSequence = new ArrayList<>();
            BranchBox.DfsMarker.clear();
            for(BranchBox c: S) c.DFS(BranchSequence);

            //Now its finally time to add our new branches to the body of our code

            Unit StmtBefore= units.getPredOf(stmt);
            Unit StmtAfter= units.getSuccOf(stmt);
            Unit IfTarget;
            units.remove(stmt);
            for(int i=0;i<BranchSequence.size();i++){
                units.insertAfter(BranchSequence.get(i).InstanceofStmt,StmtBefore);
                if(i==BranchSequence.size()-1)     IfTarget=StmtAfter;
                else IfTarget= BranchSequence.get(i+1).InstanceofStmt;
                Unit IfStmt = Jimple.v().newIfStmt(Jimple.v().newEqExpr(BranchSequence.get(i).InstanceofStmt.getLeftOp(), IntConstant.v(0)), IfTarget);
                units.insertAfter(IfStmt,BranchSequence.get(i).InstanceofStmt);
                units.insertAfter(BranchSequence.get(i).CastStmt,IfStmt);
                units.insertAfter(BranchSequence.get(i).AssignmentStmt,BranchSequence.get(i).CastStmt);
                Unit GoTo=Jimple.v().newGotoStmt(StmtAfter);
                if(i!=BranchSequence.size()-1) units.insertAfter(GoTo,BranchSequence.get(i).AssignmentStmt);  
                StmtBefore= GoTo;
            }
            //Now our part is done of making the PIC

            //Other points can jump to out removes unit
            List<Unit> pred= cfg.getPredsOf(stmt);
            for(Unit p: pred){
                if(p instanceof JGotoStmt){
                    JGotoStmt q= (JGotoStmt)p;
                    q.setTarget(BranchSequence.get(0).InstanceofStmt);
                }
                if(p instanceof JIfStmt){
                    JIfStmt q= (JIfStmt)p;
                    q.setTarget(BranchSequence.get(0).InstanceofStmt);
                }
            }
        }

        for(InvokeStmt stmt: InvokeSet){
            System.out.println("Transforming invoke statement: "+ stmt.toString());
            JVirtualInvokeExpr RhsVie= (JVirtualInvokeExpr) stmt.getInvokeExpr();
            //This is a virutalinvoke callsit which we wish to convert to multiple statics
            Iterator<Edge> OutEdges= cg.edgesOutOf(stmt);
            List<Edge> list= new ArrayList<>();
            Map<SootClass,BranchBox> Branches= new HashMap<>();
            Set<BranchBox> S= new HashSet<>();
            int BranchSize=0;
            System.out.println("OutEdges : ");
            while(OutEdges.hasNext()){
                if(MAX_BRANCH==BranchSize++) break;
                Edge e = OutEdges.next();
                System.out.println(e);
                SootMethod callee=(SootMethod)e.getTgt(); 
                //z= obj instanceof Class
                Local z= Jimple.v().newLocal("instanceofRes"+k.toString(), IntType.v()) ;
                Local RecieverCasted= Jimple.v().newLocal("RecieverCast"+k.toString(),callee.getDeclaringClass().getType());
                k++;
                AssignStmt InstanceofStmt = Jimple.v().newAssignStmt(z, Jimple.v().newInstanceOfExpr( RhsVie.getBase(),callee.getDeclaringClass().getType()));
                AssignStmt CastStmt = Jimple.v().newAssignStmt(RecieverCasted, Jimple.v().newCastExpr(RhsVie.getBase(),callee.getDeclaringClass().getType()));
                VirtualInvokeExpr VIE= Jimple.v().newVirtualInvokeExpr(RecieverCasted,callee.makeRef(), RhsVie.getArgs());
                InvokeStmt InvokeStatemnt = Jimple.v().newInvokeStmt(VIE);                                                                                 
                BranchBox b= new BranchBox(callee.getDeclaringClass(),e,InstanceofStmt,InvokeStatemnt,CastStmt);
                Branches.put(callee.getDeclaringClass(),b);
                S.add(b);
                
            }
            //If true means the branches are too many we are better off using a polymorphic call instead of a PIC
            if(BranchSize==1+MAX_BRANCH) continue;

            //Creating children relationship in branchbox taken from class heirarchy
            for(BranchBox bb: S){
                List<SootClass> l= ClassHierarchy.getDirectSubclassesOf(bb.Klass) ;
                for(SootClass c:l){
                    if(Branches.containsKey(c)) bb.addChild(Branches.get(c));
                }
            }

            //Now lets create the sequence of branching 
            List<BranchBox> BranchSequence = new ArrayList<>();
            BranchBox.DfsMarker.clear();
            for(BranchBox c: S) c.DFS(BranchSequence);

            //Now its finally time to add our new branches to the body of our code

            Unit StmtBefore= units.getPredOf(stmt);
            Unit StmtAfter= units.getSuccOf(stmt);
            Unit IfTarget;
            units.remove(stmt);
            for(int i=0;i<BranchSequence.size();i++){
                units.insertAfter(BranchSequence.get(i).InstanceofStmt,StmtBefore);
                if(i==BranchSequence.size()-1)     IfTarget=StmtAfter;
                else IfTarget= BranchSequence.get(i+1).InstanceofStmt;
                Unit IfStmt = Jimple.v().newIfStmt(Jimple.v().newEqExpr(BranchSequence.get(i).InstanceofStmt.getLeftOp(), IntConstant.v(0)), IfTarget);
                units.insertAfter(IfStmt,BranchSequence.get(i).InstanceofStmt);
                units.insertAfter(BranchSequence.get(i).CastStmt,IfStmt);
                units.insertAfter(BranchSequence.get(i).AssignmentStmt,BranchSequence.get(i).CastStmt);
                Unit GoTo=Jimple.v().newGotoStmt(StmtAfter);
                if(i!=BranchSequence.size()-1) units.insertAfter(GoTo,BranchSequence.get(i).AssignmentStmt);  
                StmtBefore= GoTo;
            }
            //Now our part is done of making the PIC

            //Other points can jump to out removes unit
            List<Unit> pred= cfg.getPredsOf(stmt);
            for(Unit p: pred){
                if(p instanceof JGotoStmt){
                    JGotoStmt q= (JGotoStmt)p;
                    q.setTarget(BranchSequence.get(0).InstanceofStmt);
                }
                if(p instanceof JIfStmt){
                    JIfStmt q= (JIfStmt)p;
                    q.setTarget(BranchSequence.get(0).InstanceofStmt);
                }
            }
        }

        for(Unit u: units){
            System.out.println(u);
            // if(u instanceof JAssignStmt){
            //     JAssignStmt stmt= (JAssignStmt)u;
            //     Value rhs= stmt.getRightOp();
            //     System.out.println("R: "+ rhs.getClass().toString());
            // }
        }
        //Printing the units
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
