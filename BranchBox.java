import java.util.*;
import soot.*;
import soot.SootMethod;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.jimple.internal.*;
import soot.options.Options;
zpublic class BranchBox{
    static Set<BranchBox> DfsMarker= new HashSet<>(); 
    SootClass Klass; Edge edge; JAssignStmt InstanceofStmt; JAssignStmt AssignmentStmt;
    Set<SootClass> Children= new HashSet<>();
    public BranchBox(SootClass c, Edge e,JAssignStmt a, JAssignStmt b){
        Klass=c; edge=e; InstanceofStmt=a; AssignmentStmt=b;
    }
    public void addChild(SootClass c){
        Children.add(c);
    }
    public Set<SootClass> getChildren(){ return Children;}
    public void DFS(List<SootClass> list){
        if(DfsMarker.contains(this)) return;
        for(SootClass child: Children) child.DFS(list);
        DfsMarker.add(this);
        list.add(this);
    }
}
