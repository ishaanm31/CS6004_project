class TestNode {
	TestNode f;
	TestNode g;
	TestNode() {}
	void car(){
		System.out.println("hoho");
	}
}
class ChildTestNode extends TestNode{
	void car(){
		System.out.println("nono");
	}
}

public class Test {
	public static TestNode global;
	public static void main(String[] args) {
		foo();
		TestNode t= new TestNode();
		t.car();
	}
	public static TestNode foo(){
		TestNode x = new TestNode();
		TestNode y = new ChildTestNode();
		if(y==null) y=x;
		y.car();
		return x;
	}
}