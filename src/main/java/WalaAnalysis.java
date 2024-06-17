import com.ibm.wala.classLoader.*;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.core.util.io.FileProvider;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class WalaAnalysis {

    public static void main(String[] args) throws
            IOException, ClassHierarchyException, CallGraphBuilderCancelException, WalaException, CancelException {

        String pathToTests = Paths.get("src/main/resources/TestCases").toAbsolutePath().toString();
        File exclusionsFile = new File("src/main/resources/exclusions.txt");
        WalaAnalysis myWalaAnalyzer = new WalaAnalysis();

        myWalaAnalyzer.MakeWalaAnalysis(pathToTests, exclusionsFile);




       // myWalaAnalyzer.makeCallGraphAnalysis(jarPath, exFile);
        //myWalaAnalyzer.makeClassHierarchyAnalysis(jarPath, exFile);
       // Graph<CGNode>finalGraph = pruneGraph(myWalaAnalyzer.makeCallGraphAnalysis(jarPath, exFile));
        //myWalaAnalyzer.makeClassHierarchyAnalysis(javaClassPath,exFile);

    }

    public void MakeWalaAnalysis(String javaClassPath, File exclusionsFile)
    {
        try {

            TestSuiteReader testSuiteReader = new TestSuiteReader();
            List<String> testSuitePackages = testSuiteReader.GetTestSuitePaths(javaClassPath);

            for (String packagePath : testSuitePackages) {
                List<String> classPaths = testSuiteReader.GetFullPathClasses(packagePath);
                for (String classPath : classPaths) {

                    System.out.println("\n==================== "
                            + Paths.get(classPath).getFileName().toString().split(Pattern.quote("."))[0] + " ====================\n");
                    AnalysisScope scope1 = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(classPath, exclusionsFile);

                    ClassHierarchy cha = ClassHierarchyFactory.make(scope1);

                    Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(cha);
                    AnalysisOptions options = new AnalysisOptions(scope1, entrypoints);

                    IAnalysisCacheView cacheView = new AnalysisCacheImpl();
                    CallGraphBuilder<?> builder = Util.makeZeroOneCFABuilder(Language.JAVA,options,cacheView,cha);
                    CallGraph cg = builder.makeCallGraph(options, null);

                    printCallGraph(cg);
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isInternalJavaClass(String className) {
        return className.startsWith("Ljava") ||
                className.startsWith("Ljdk") ||
                className.startsWith("Lcom/ibm");
    }

    private static void printCallGraph(CallGraph cg) {
        // Iterate over all nodes in the call graph
        for (CGNode node : cg) {
            String className = node.getMethod().getDeclaringClass().getName().toString();

            // Skip internal Java classes
            if (isInternalJavaClass(className)) {
                continue;
            }

            System.out.println("Method: " + node.getMethod().getSignature());
            //prints Jimple of a method
            //System.out.println(node.getIR().toString());
            // Print callers
            System.out.print("  - Callers: ");
            for (Iterator<CGNode> it = cg.getPredNodes(node); it.hasNext(); ) {
                CGNode caller = it.next();
                String callerClassName = caller.getMethod().getDeclaringClass().getName().toString();
                if (!isInternalJavaClass(callerClassName)) {
                    System.out.print(caller.getMethod().getSignature() + ", ");
                }
            }
            System.out.println();

            // Print callees
            System.out.print("  - Callees: ");
            for (Iterator<CGNode> it = cg.getSuccNodes(node); it.hasNext(); ) {
                CGNode callee = it.next();
                String calleeClassName = callee.getMethod().getDeclaringClass().getName().toString();
                if (!isInternalJavaClass(calleeClassName)) {
                    System.out.print(callee.getMethod().getSignature() + ", ");
                }
            }
            System.out.println();
        }
    }
    private static AnalysisScope createAnalysisScope(String classFilePath, File exFile) throws IOException, ClassHierarchyException {
        // Create a temporary file for the scope file

        File scopeFile = File.createTempFile("scope", ".txt");

        String scopeFileContent = "Primordial,Java,stdlib,none\n" + "Application,Java,jarFile," + classFilePath;
        try (PrintWriter writer = new PrintWriter(new FileWriter(scopeFile))) {
            writer.println(scopeFileContent);
        }
        // Create an AnalysisScope using the scope file
        return AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope(scopeFile.getPath(), exFile);
    }

    private static void printMethodsInCallGraph(CallGraph cg) {
        // Iterate over all classes in the call graph
        for (IClass clazz : cg.getClassHierarchy()) {
            // Iterate over all methods in each class
            for (IMethod method : clazz.getDeclaredMethods()) {
                System.out.println("Method: " + method.getSignature());
            }
        }
    }

//======================================================================================================================
    public void makeClassHierarchyAnalysis(String jarPath, File exFile) throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {

        System.out.println(exFile.getAbsolutePath());
        AnalysisScope scope = AnalysisScopeReader.instance.makeJavaBinaryAnalysisScope
                (jarPath, exFile);

        IClassHierarchy cha = ClassHierarchyFactory.makeWithPhantom(scope);

        Iterable entrypoints = Util.makeMainEntrypoints(scope, cha);
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
        options.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);


        for (IClass c : cha) {
            if (!scope.isApplicationLoader(c.getClassLoader())) continue;
            String cname = c.getName().toString();
            System.out.println("Class:" + cname);
            for (IMethod m : c.getAllMethods()) {
                String mname = m.getName().toString();
                System.out.println("  method:" + mname);
            }
            System.out.println();

        }
    }

    private static Graph<CGNode> pruneGraph(CallGraph callgraph) {
        Graph<CGNode> finalGraph = GraphSlicer.prune(callgraph, new Predicate<CGNode>() {
            @Override
            public boolean test(CGNode t) {
                IMethod meth = t.getMethod();
                t.iterateCallSites();
                String classLoader = meth.getDeclaringClass().getClassLoader().toString();
                return classLoader.equals("Application");
            }
        });
        return finalGraph;
    }
}
