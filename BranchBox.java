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
public class BranchBox{
    static Set<BranchBox> DfsMarker= new HashSet<>(); 
    SootClass Klass; Edge edge; JAssignStmt InstanceofStmt; JAssignStmt AssignmentStmt;
    Set<BranchBox> Children= new HashSet<>();
    public BranchBox(SootClass c, Edge e,JAssignStmt a, JAssignStmt b){
        Klass=c; edge=e; InstanceofStmt=a; AssignmentStmt=b;
    }
    public void addChild(BranchBox c){
        Children.add(c);
    }
    public Set<BranchBox> getChildren(){ return Children;}
    public void DFS(List<BranchBox> list){
        if(DfsMarker.contains(this)) return;
        for(BranchBox child: Children) child.DFS(list);
        DfsMarker.add(this);
        list.add(this);
    }
}
