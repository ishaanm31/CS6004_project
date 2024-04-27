import java.util.*;

public class PointsToGraph {
    HashMap<String,ReferenceVarNode> ReferenceVarList;
    HashMap<String,ObjectNode> ObjectNodeList;
    
    public PointsToGraph(){
        this.ReferenceVarList = new HashMap<>();
        this.ObjectNodeList = new HashMap<>();
    }

    public PointsToGraph(PointsToGraph other){
        this.ReferenceVarList = new HashMap<>();
        this.ObjectNodeList = new HashMap<>();
        for (String key : other.ReferenceVarList.keySet()){
            ReferenceVarNode temp = new ReferenceVarNode(other.ReferenceVarList.get(key));
            this.ReferenceVarList.put(key, temp);
        }
        for (String key: other.ObjectNodeList.keySet()){
            ObjectNode temp = new ObjectNode(other.ObjectNodeList.get(key));
            this.ObjectNodeList.put(key, temp);
        }
    }

    public boolean areEqual(PointsToGraph other){
        boolean flag = true;
        if (this.ReferenceVarList.keySet().equals(other.ReferenceVarList.keySet())){
            for (String s: this.ReferenceVarList.keySet()){
                if (!this.ReferenceVarList.get(s).areEqual(other.ReferenceVarList.get(s))){
                    flag = false;
                    break;
                }
            }
        }
        else{
            flag = false;
        }
        if (this.ObjectNodeList.keySet().equals(other.ObjectNodeList.keySet())){
            for (String s: this.ObjectNodeList.keySet()){
                if (!this.ObjectNodeList.get(s).areEqual(other.ObjectNodeList.get(s))){
                    flag = false;
                    break;
                }
            }
        }
        else{
            flag = false;
        }

        return flag;
    }

    private HashMap<String,ReferenceVarNode> getReferenceVarList(){
        return this.ReferenceVarList;
    }

    private HashMap<String,ObjectNode> getObjectNodeList(){
        return this.ObjectNodeList;
    }

    public Set<String> getReferenceVarNameList(){
        return this.ReferenceVarList.keySet();
    }

    public Set<String> getObjectNodeNameList(){
        return this.ObjectNodeList.keySet();
    }

    public void printPtg(){
        for (String s: this.ReferenceVarList.keySet()){
            System.out.println(s);
            this.ReferenceVarList.get(s).printRefVar();
            System.out.println();
        }
        System.out.println();
        for (String s: this.ObjectNodeList.keySet()){
            System.out.println(s);
            this.ObjectNodeList.get(s).printObjectNode();
            System.out.println();
        }
    }

    private void createRefNode(String name){
        if (!this.ReferenceVarList.containsKey(name)){
            ReferenceVarNode add = new ReferenceVarNode();
            ReferenceVarList.put(name, add);
        }
    }

    private void createObjectNode(String name){
        if (!this.ObjectNodeList.containsKey(name)){
            ObjectNode add = new ObjectNode();
            ObjectNodeList.put(name, add);
        }
    }

    public void addRefEdge(String refVarName, String objName){
        this.createRefNode(refVarName);
        this.createObjectNode(objName);
        ReferenceVarList.get(refVarName).addEdge(objName);
    }

    public void addObjEdge(String firstObjName, String secondObjName, String field){
        this.createObjectNode(firstObjName);
        this.createObjectNode(secondObjName);
        ObjectNodeList.get(firstObjName).addEdge(field, secondObjName);
    }

    public void takeUnion(ArrayList<PointsToGraph> parentUnitsGraphs){
        for (PointsToGraph g: parentUnitsGraphs){
            HashMap<String,ReferenceVarNode> currReferenceVarList = g.getReferenceVarList();
            for (String refVar: currReferenceVarList.keySet()){
                for (String objName: currReferenceVarList.get(refVar).AdjList){
                    this.addRefEdge(refVar, objName);
                }
            }
            HashMap<String,ObjectNode> currObjectNodeList = g.getObjectNodeList();
            for (String firstObjName: currObjectNodeList.keySet()){
                for (String field: currObjectNodeList.get(firstObjName).AdjList.keySet()){
                    for (String secondObjName: currObjectNodeList.get(firstObjName).AdjList.get(field)){
                        this.addObjEdge(firstObjName, secondObjName, field);
                    }
                }
            }
        }
    }

    public HashSet<String> getReferenceVarAdjList(String refVarName){
        if (this.ReferenceVarList.containsKey(refVarName)){
            return this.ReferenceVarList.get(refVarName).getAdjList();
        }
        else{
            return new HashSet<>();
        }
    }

    public HashSet<String> getObjectNodeAdjList(String objName, String field){
        if (this.ObjectNodeList.containsKey(objName)){
            if (this.ObjectNodeList.get(objName).getAdjList().get(field) == null){
                return new HashSet<>();
            }
            return this.ObjectNodeList.get(objName).getAdjList().get(field);
        }
        else{
            return new HashSet<>();
        }
            
    }

    public HashMap<String, HashSet<String>> getObjectNodeFull(String objName){
        if (this.ObjectNodeList.containsKey(objName)){
            return this.ObjectNodeList.get(objName).getAdjList();
        }
        else{
            return new HashMap<>();
        }
    }

    public void clearReferenceVarEdges(String refVarName){
        if (this.ReferenceVarList.containsKey(refVarName)){
            this.ReferenceVarList.get(refVarName).clearAdjList();
        } 
    }

    public boolean refVarExists(String refVarName){
        return this.ReferenceVarList.containsKey(refVarName);
    }
}

class ReferenceVarNode {
    HashSet<String> AdjList;
    
    public ReferenceVarNode(){
        this.AdjList = new HashSet<>();
    }

    public ReferenceVarNode(ReferenceVarNode other){
        HashSet<String> temp = new HashSet<>();
        for (String a: other.AdjList){
            temp.add(a);
        }
        this.AdjList = temp;
    }

    public void printRefVar(){
        for (String s: this.AdjList){
            System.out.print(s+" ");
        }
    }

    public boolean areEqual(ReferenceVarNode other){
        return this.AdjList.equals(other.AdjList);
    }

    public void addEdge(String objNodeName){
        this.AdjList.add(objNodeName);
    }

    public void removeEdge(String objNodeName){
        if (this.AdjList.contains(objNodeName)){
            this.AdjList.remove(objNodeName);
        }
    }

    public HashSet<String> getAdjList(){
        return this.AdjList;
    }

    public void clearAdjList(){
        this.AdjList.clear();
    }
}

class ObjectNode {
    HashMap<String, HashSet<String>> AdjList;

    public ObjectNode(){
        this.AdjList = new HashMap<>();
    }

    public ObjectNode(ObjectNode other){
        HashMap<String, HashSet<String>> outer = new HashMap<>();
        for (String s: other.AdjList.keySet()){
            HashSet<String> inner = new HashSet<>();
            for (String r: other.AdjList.get(s)){
                inner.add(r);
            }
            outer.put(s, inner);
        }
        this.AdjList = outer;
    }

    public void printObjectNode(){
        for (String f: this.AdjList.keySet()){
            System.out.println(f);
            for (String g: this.AdjList.get(f)){
                System.out.print(g+" ");
            }
            System.out.println();
        }
    }

    public boolean areEqual(ObjectNode other){
        boolean flag = true;
        if (this.AdjList.keySet().equals(other.AdjList.keySet())){
            for (String s: this.AdjList.keySet()){
                if (!this.AdjList.get(s).equals(other.AdjList.get(s))){
                    flag = false;
                    break;
                }
            }
        }
        else{
            flag = false;
        }
        return flag;
    }

    private void createField(String field){
        if (!AdjList.containsKey(field)){
            HashSet<String> add = new HashSet<>();
            this.AdjList.put(field, add);
        }
    }
    
    public void addEdge(String field, String objNodeName){
        this.createField(field);
        this.AdjList.get(field).add(objNodeName);
    }

    public HashMap<String, HashSet<String>> getAdjList(){
        return this.AdjList;
    }
}
