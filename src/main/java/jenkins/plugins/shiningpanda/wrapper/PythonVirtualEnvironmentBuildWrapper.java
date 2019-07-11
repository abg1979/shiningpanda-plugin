package jenkins.plugins.shiningpanda.wrapper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.plugins.shiningpanda.interpreters.Python;
import jenkins.plugins.shiningpanda.interpreters.Virtualenv;
import jenkins.plugins.shiningpanda.tools.PythonInstallation;
import jenkins.plugins.shiningpanda.utils.BuilderUtil;
import jenkins.plugins.shiningpanda.workspace.Workspace;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class PythonVirtualEnvironmentBuildWrapper extends BuildWrapper {
    /**
     * Name of the PYTHON to invoke
     */
    private final String pythonName;

    /**
     * Home folder for this VIRTUALENV
     */
    private final String home;

    /**
     * Clear out the non-root install and start from scratch
     */
    private boolean clear;

    /**
     * Give access to the global site-packages
     */
    private boolean systemSitePackages;

    class PythonVirtualEnvironment extends Environment {
        private Map<String, String> variables;

        PythonVirtualEnvironment(Map<String, String> variables) {
            this.variables = variables;
            Iterator<Map.Entry<String, String>> iterator = this.variables.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> next = iterator.next();
                if (next.getValue() == null) {
                    iterator.remove();
                }
            }
        }

        public Map<String, String> getVariables() {
            return variables;
        }

        @Override
        public void buildEnvVars(Map<String, String> env) {
            env.putAll(variables);
        }
    }

    @DataBoundConstructor
    public PythonVirtualEnvironmentBuildWrapper(String pythonName, String home, boolean clear, boolean systemSitePackages) {
        this.pythonName = pythonName;
        this.home = home;
        this.clear = clear;
        this.systemSitePackages = systemSitePackages;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        Workspace workspace = Workspace.fromBuild(build);
        // Get the environment variables for this build
        EnvVars environment = BuilderUtil.getEnvironment(build, listener);
        // Check if this is a valid environment
        if (environment == null)
            // Invalid, no need to go further
            return null;
        // Get the PYTHON installation to use
        PythonInstallation installation = BuilderUtil.getInstallation(build, listener, environment, pythonName);
        // Check if an installation was found
        if (installation == null)
            // If not installation found, do not continue the build
            return null;
        // Get the PYTHON
        Python interpreter = BuilderUtil.getInterpreter(launcher, listener, installation.getHome());
        // Check if found an interpreter
        if (interpreter == null)
            // If no interpreter found, no need to continue
            return null;
        // Create a VIRTUALENV
        Virtualenv virtualenv = BuilderUtil.getVirtualenv(listener, workspace.getVirtualenvHome(home));
        // Check if this is a valid VIRTUALENV
        if (virtualenv == null)
            // Invalid VIRTUALENV, do not continue
            return null;
        // Get the working directory
        FilePath pwd = build.getWorkspace();
        // Check if clean required or if configuration changed
        if (clear || virtualenv.isOutdated(workspace, interpreter, systemSitePackages))
            // A new environment is required
            if (!virtualenv.create(launcher, listener, workspace, pwd, environment, interpreter, systemSitePackages)) {
                // Failed to create the environment, do not continue
                return null;
            }
        // Get PYTHON executable
        String executable = virtualenv.getExecutable().getRemote();
        // Set the interpreter environment
        Map<String, String> variables = virtualenv.getEnvironment();
        // Add PYTHON_EXE environment variable
        variables.put("PYTHON_EXE", executable);
        return new PythonVirtualEnvironment(variables);
    }

    @Override
    public void makeBuildVariables(AbstractBuild build, Map<String, String> variables) {
        PythonVirtualEnvironment environment = build.getEnvironments().get(PythonVirtualEnvironment.class);
        if (environment != null) {
            variables.putAll(environment.getVariables());
        }
    }

    @Extension(ordinal = 1000)
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
            super(PythonVirtualEnvironmentBuildWrapper.class);
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        public String getDisplayName() {  //This implementation needs to be modified
            return "Python Virtual Environment Build Wrapper";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

        @Override
        public String getHelpFile() {
            return Functions.getResourcePath() + "/plugin/shiningpanda/help/builders/VirtualenvBuilder/help.html";
        }

        public boolean isMatrix(Object it) {
            return it instanceof MatrixProject;
        }

        /**
         * Get the PYTHON installations.
         *
         * @return The list of installations
         */
        public PythonInstallation[] getInstallations() {
            // Delegate
            return PythonInstallation.list();
        }

    }

}
