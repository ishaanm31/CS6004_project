import java.util.*;

class contain_objects{
    ArrayList<Integer> contain = new ArrayList<>();
    contain_objects(){
        for (Integer i=0; i<500000; i++){
            contain.add(i);
        }
    }
    public Integer getSize(){
        return contain.size();
    } 
}

public class Test_null_1 {
    public static void main(String[] args){
        contain_objects test1 = new contain_objects();
        System.out.println(test1.getSize());
        contain_objects test2 = new contain_objects();
        System.out.println(test2.getSize());
        contain_objects test3 = new contain_objects();
        System.out.println(test3.getSize());
        contain_objects test4 = new contain_objects();
        System.out.println(test4.getSize());
        contain_objects test5 = new contain_objects();
        System.out.println(test5.getSize());
        contain_objects test6 = new contain_objects();
        System.out.println(test6.getSize());
        System.gc();
    }
}
