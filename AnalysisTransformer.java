import java.util.*;
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

public class AnalysisTransformer extends SceneTransformer {
    static CallGraph cg;
    @Override
    protected void internalTransform(String arg0, Map<String, String> arg1) {
        Set<SootMethod> methods = new HashSet <>();
        cg = Scene.v().getCallGraph();
        // Get the main method
        SootMethod mainMethod = Scene.v().getMainMethod();
        processCFG(mainMethod);
        GarbageCollector.PrintResult();
    }

    protected static void processCFG(SootMethod method) {
        Node StringArg= new Node();
        Set<Node> S= new HashSet<Node>();
        S.add(StringArg);
        List<Set<Node>> param=new ArrayList<Set<Node>>();
        param.add(S);
        PointToAnalysis(method, null,param);
    }

    //Function would create MethodBlock for each method that it is called for
    // NodeThis -> Pass the object on which the function is called, As there would be no static fields in a class we can pass Static methods with Null
    // ParameterNodes need to be created by the parent/callee and then pass the Nodes for it to change
    protected static Set<Node> PointToAnalysis (SootMethod method, Set<Node> NodeThis, List<Set<Node>>ParameterNodeList){
        if(method.isConstructor()) return null;
        GarbageCollector. AllMethods.add(method);
        Body body = method.getActiveBody();              //Get the Body
        UnitGraph cfg = new BriefUnitGraph(body);        // Get the callgraph 
        LiveLocals liveLocals = new SimpleLiveLocals(cfg);        // Get live local using Soot's exiting analysis
        PatchingChain<Unit> units = body.getUnits();     //Getting units from our body these are a linked list of jimple stmts
        Map<Unit,PTG> UnitToPtgMapAfter=new HashMap<Unit,PTG>();         //PTG after each unit in our body
        Map<Unit,PTG> UnitToPtgMapBefore=new HashMap<Unit,PTG>();         //PTG after each unit in our body
        Queue<Unit> Worklist= new LinkedList<Unit>(units); //Worklist for the units whose predecessors ptg has been altered

        // System.out.println("\n----- " + body.getMethod().getName() + "-----");
        for (Unit u : units) {
            PTG p = new PTG(NodeThis,ParameterNodeList);
            UnitToPtgMapAfter.put(u,p);
            //System.out.println(u.getClass()+" | "+u);
        }
        PTG ReturnPTG= new PTG(NodeThis,ParameterNodeList);
        while(!Worklist.isEmpty()){
            Unit curr= Worklist.poll();
            //System.out.println(curr.toString());
            //System.out.println(curr);
            List<Unit> preds = cfg.getPredsOf(curr);
            PTG p= new PTG(NodeThis,ParameterNodeList);
            // System.out.println("In pred");
            for(Unit pred: preds){
                p.Merge(UnitToPtgMapAfter.get(pred));
            }
            //Maintaining UnitToPtgMapBefore
            PTG pBefore= new PTG(NodeThis,ParameterNodeList); pBefore.Merge(p); 
            UnitToPtgMapBefore.put(curr,pBefore);

            //Executing the statement
            p.proc_stmt(curr);

            // System.out.println("After Exec");

            //Check if the it made a difference
            if(UnitToPtgMapAfter.get(curr).Merge(p)){ //Merge returns a bool if true means A.merge(B) have A and B different
                UnitToPtgMapAfter.remove(curr);
                UnitToPtgMapAfter.put(curr,p);
                for(Unit suc: cfg.getSuccsOf(curr)){
                    if(!Worklist.contains(suc)) Worklist.add(suc);
                }
            }   
            if((curr instanceof JReturnStmt) | (curr instanceof JReturnVoidStmt)){
                ReturnPTG.Merge(UnitToPtgMapAfter.get(curr));
            }
        }

        //At this point we are done with Points to analysis (interprocedural lesgoo!)
        //Now we start with analysis of Garbage Collection
        for(Unit u: units){
            List<Local> before = liveLocals.getLiveLocalsBefore(u);
            List<Local> after = liveLocals.getLiveLocalsAfter(u);
            // System.out.println((new Integer(u.getJavaSourceStartLineNumber())).toString()+"|"+u.toString()+ " | "+before.toString()+" | "+ after.toString());
            Set<Node> ReturnVoid=null;
            if(u instanceof JInvokeStmt) ReturnVoid= UnitToPtgMapAfter.get(u).ReturnVoidSet;
            GarbageCollector.Process(method,u,UnitToPtgMapBefore.get(u),UnitToPtgMapAfter.get(u),before,after, ReturnVoid);
            //UnitToPtgMapAfter.get(u).printPTG();
        }
        ReturnPTG.AffectExternalNodes();
        // System.out.println("\nxxxxx " + body.getMethod().getName() + "xxxxx");
        return ReturnPTG.getRetNodes();
    }
    public static Set<Node> PointToAnalysis (Unit CallStmt, Set<Node> NodeThis, List<Set<Node>>ParameterNodeList){
        //System.out.println(CallStmt.toString());
        Iterator<Edge> OutEdges= cg.edgesOutOf(CallStmt);
        Set<Node> RetNodes=new HashSet<>();
        while(OutEdges.hasNext()){
            RetNodes.addAll(PointToAnalysis (OutEdges.next().tgt(), NodeThis, ParameterNodeList));
        }
        return RetNodes;
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




    