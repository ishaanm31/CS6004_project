class TestNode {
	TestNode f;
	TestNode g;
	TestNode() {}
}

public class Test {
	public static TestNode global;
	public static void main(String[] args) {
		foo();
	}
	public static TestNode foo(){
		TestNode x = new TestNode();
		TestNode y = new TestNode();
		y.f = new TestNode();
		y = new TestNode();
		bar(x, y);
		TestNode z = y.f;
		TestNode a = x.f;
		return x;
	}
	public static void bar(TestNode p1, TestNode p2){
		TestNode v = new TestNode();
		p1.f = v;	
	}
}