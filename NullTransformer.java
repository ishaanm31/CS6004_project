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

public class NullTransformer extends BodyTransformer {
    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        // Get the control flow graph 
        UnitGraph cfg = new BriefUnitGraph(body);

        // Get live local using Soot's exiting analysis
        LiveLocals liveLocals = new SimpleLiveLocals(cfg);

        // get function units
        PatchingChain<Unit> units = body.getUnits();

        HashMap<Unit, ArrayList<JAssignStmt>> newStmts = new HashMap<>();
        for (Unit u: units){
            newStmts.put(u, new ArrayList<>());
            List<Local> before = liveLocals.getLiveLocalsBefore(u);
            List<Local> after = liveLocals.getLiveLocalsAfter(u);
            List<Local> unused = new ArrayList<>(); 
            for (Local l: before){
                if (!after.contains(l)){
                    unused.add(l);
                }
            }     
            for (Local l: unused){
                JAssignStmt newUnit = new JAssignStmt(l, NullConstant.v());
                newStmts.get(u).add(newUnit);
            }
        }
        for (Unit u: newStmts.keySet()){
            units.insertAfter(newStmts.get(u), u);
        }
    }
}
