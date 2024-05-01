class TestNode {
	TestNode f;
	TestNode g;
	TestNode() {}
	void car(){
		System.out.println("hoho");
		// return this;
	}
}
// class Yolo extends TestNode{
// 	void car(){
// 		System.out.println("nono");
// 		// return this;
// 	}
// }

public class Test {
	public static TestNode global;
	public static void main(String[] args) {
		foo();
		TestNode t= new TestNode();
		t.car();
	}
	public static TestNode foo(){
		TestNode x = new TestNode();
		// TestNode y = new Yolo();
		//if(y==null) y=x;
		Integer xx=0;
		xx=null;
		return x;

	}
	public static void bar(TestNode p1, TestNode p2){
		TestNode v = new TestNode();
		p1.f = v;	
	}
}