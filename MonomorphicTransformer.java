import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import javax.swing.text.html.Option;

import org.omg.CORBA.portable.ValueBase;

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
import soot.options.*;
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
    static SootClass StaticSootClass;
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
        //StaticSootClass= new SootClass("StaticClass") ; //Static Class to contain all the static methods converted from virtual methods
        StaticSootClass= mainMethod.getDeclaringClass();
        // for(SootMethod Method: AllMethods){
        //     Transform(Method);            //This will individually transform each method 
        // }
        Transform(mainMethod);
    }

    synchronized private void Transform(SootMethod Method){
        System.out.println("Method: "+ Method.toString());
        //Getting unit chain of the method
        Body body=Method.getActiveBody();   //Body of the Method
        ExceptionalUnitGraph cfg= new ExceptionalUnitGraph(body); //Control Flow Graph
        PatchingChain<Unit> units= body.getUnits();  //Units of the body
        Set<JAssignStmt> AssignSet= new HashSet<>(); //Contains all the x=y.foo() units
        Set<JInvokeStmt> InvokeSet= new HashSet<>(); //Contains all z.bar() units

        Set<JInvokeStmt> S= new HashSet<>();
        Set<JAssignStmt> L= new HashSet<>();
        for(Unit u: units){
            if(u instanceof JInvokeStmt){
                JInvokeStmt stmt= (JInvokeStmt)u;
                Value expr= stmt.getInvokeExpr();
                if(!(expr instanceof JVirtualInvokeExpr)) continue;
                Iterator<Edge> OutEdges= cg.edgesOutOf(stmt);
                List<Edge> OE= new ArrayList<>();
                while(OutEdges.hasNext()) OE.add(OutEdges.next());
                System.err.println(OE.size());
                if(OE.size()==1) S.add(stmt);                                                        
            }
            if(u instanceof JAssignStmt){
                JAssignStmt stmt= (JAssignStmt)u;
                Value expr= stmt.getRightOp();
                if(expr instanceof JVirtualInvokeExpr){
                    JVirtualInvokeExpr exprv=  (JVirtualInvokeExpr)expr;
                    Iterator<Edge> OutEdges= cg.edgesOutOf(stmt);
                    List<Edge> OE= new ArrayList<>();
                    while(OutEdges.hasNext()) OE.add(OutEdges.next());
                    System.err.println(OE.size());
                    if(OE.size()==1) L.add(stmt);
                }
                                                                       
            }
        }
        for(JInvokeStmt s:S){
            System.out.println(s);
            VirtualInvokeExpr expr= (VirtualInvokeExpr) s.getInvokeExpr();
            Iterator<Edge> OutEdges= cg.edgesOutOf(s);
            Edge onlyEdge= OutEdges.next();
            SootMethod targetMethod= (SootMethod)onlyEdge.getTgt();
            VirtualInvokeExpr VIE= Jimple.v().newVirtualInvokeExpr((Local)expr.getBase(),targetMethod.makeRef(), expr.getArgs());
            List<Value> NewArgs= new ArrayList<>();
            SootMethod StaticMethod= MakeStaticMethod(targetMethod);
            NewArgs.addAll(expr.getArgs());
            NewArgs.add(expr.getBase());
            System.out.println(StaticMethod);
            System.out.println(NewArgs);
            System.out.println(StaticMethod.makeRef().getDeclaringClass());
            StaticInvokeExpr SIE= new JStaticInvokeExpr(StaticMethod.makeRef(),NewArgs);
            InvokeStmt InvokeStatemnt = Jimple.v().newInvokeStmt(SIE);       
            units.insertAfter(InvokeStatemnt,s); 
            units.remove(s);
        }
        for(JAssignStmt s:L){
            System.out.println(s);
            VirtualInvokeExpr expr= (VirtualInvokeExpr) s.getRightOp();
            Iterator<Edge> OutEdges= cg.edgesOutOf(s);
            Edge onlyEdge= OutEdges.next();
            SootMethod targetMethod= (SootMethod)onlyEdge.getTgt();
            VirtualInvokeExpr VIE= Jimple.v().newVirtualInvokeExpr((Local)expr.getBase(),targetMethod.makeRef(), expr.getArgs());
            List<Value> NewArgs= new ArrayList<>();
            SootMethod StaticMethod= MakeStaticMethod(targetMethod);
            NewArgs.addAll(expr.getArgs());
            NewArgs.add(expr.getBase());
            System.out.println(StaticMethod);
            System.out.println(NewArgs);
            System.out.println(StaticMethod.makeRef().getDeclaringClass());
            StaticInvokeExpr SIE= new JStaticInvokeExpr(StaticMethod.makeRef(),NewArgs);
            AssignStmt AssignStatement = Jimple.v().newAssignStmt(s.getLeftOp(),SIE);       
            units.insertAfter(AssignStatement,s); 
            units.remove(s);
        }
        for(Unit u: units){
            System.out.println(u);
        }
        //Printing the units
    }
    private static SootMethod MakeStaticMethod(SootMethod VMethod){
        List<Type> ParamType= new ArrayList<>();
        ParamType.addAll( VMethod.getParameterTypes());        //Rest Type
        Type TypeofThis= VMethod.getDeclaringClass().getType();
        ParamType.add(TypeofThis); //Type of this
        SootMethod SMethod= Scene.v().makeSootMethod(VMethod.getName().toString()+"Static", ParamType, VMethod.getReturnType()) ;
        Body body= Jimple.v().newBody(SMethod);
        UnitPatchingChain sunits= body.getUnits();
        Unit newU;
        for(Unit u: VMethod.getActiveBody().getUnits()){
            newU=u;
            if(u instanceof IdentityStmt){
                IdentityStmt stmt = (IdentityStmt)u; 
                Value lhs= stmt.getLeftOp();
                IdentityRef rhs= (IdentityRef)stmt.getRightOp();
                if(rhs instanceof ThisRef) {
                    ParameterRef PThisRef= Jimple.v().newParameterRef(TypeofThis,ParamType.size()-1);
                    newU = Jimple.v().newIdentityStmt(lhs,PThisRef);
                }
            }
            sunits.add(newU);
        }
        SMethod.setActiveBody(body);
        StaticSootClass.addMethod(SMethod);
        SMethod.setDeclaringClass(StaticSootClass);
        System.out.println("Static method:");
        for(Unit u:sunits){
            System.out.println(u);
        }
        System.out.println("Static method Ended");
        return SMethod;
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
