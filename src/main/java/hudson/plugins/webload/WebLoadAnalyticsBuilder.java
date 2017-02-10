// (c) Copyright 2013 RadView Software Inc. 
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
package hudson.plugins.webload;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author yams
 */
public class WebLoadAnalyticsBuilder extends Builder /*Recorder*/ {

    public enum OutputFormats { JUNIT, HTML,DOC,ODT,XLS,XLSX,RTF,PDF,CSV,RAW }
    
    private final String inputLsFile;
    private final String portfolioFile;
    private final OutputFormats format;
    private final String location;
    private final String reportName;
    private final String compareToSessions;
    private final int compareToPreviousBuilds;

    @DataBoundConstructor
    public WebLoadAnalyticsBuilder(String inputLsFile, String portfolioFile, OutputFormats format, String location, String reportName, String compareToSessions, int compareToPreviousBuilds) {
        this.inputLsFile = inputLsFile;
        this.portfolioFile = portfolioFile;
        this.format = format;
        this.location = location;
        this.reportName = reportName;
        this.compareToSessions = compareToSessions;
        this.compareToPreviousBuilds = compareToPreviousBuilds;
    }
    
    
    public String getInputLsFile() {
        return inputLsFile;
    }

    public String getPortfolioFile() {
        return portfolioFile;
    }

    public OutputFormats getFormat() {
        return format;
    }

    public String getLocation() {
        return location;
    }

    public String getReportName() {
        return reportName;
    }

    public String getCompareToSessions() {
        return compareToSessions;
    }
    
    public int getCompareToPreviousBuilds() {
        return compareToPreviousBuilds;
    }
    
        @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		EnvVars envVars = build.getEnvironment(listener);
		// on Windows environment variables are converted to all upper case,
		// but no such conversions are done on Unix, so to make this
		// cross-platform,
		// convert variables to all upper cases.
		for (Map.Entry<String, String> e : build.getBuildVariables().entrySet())
			envVars.put(e.getKey(), e.getValue());

		String path = envVars.get("WL_HOME");
		if ((path != null) && path.length() > 0) {
			listener.getLogger().println("Using WL_HOME:" + path);
		} else {
			path = getDescriptor().getWebloadInstallationPath();
			if ((path != null) && path.length() > 0) {
				listener.getLogger().println("Using WebloadInstallationPath:" + path);
			} else {
				String programFile = envVars.get("ProgramFiles(x86)");
				if (programFile == null) {
					programFile = envVars.get("ProgramFiles");
				}
				if (programFile == null) {
					programFile = "C:\\Program Files";
				}
				path = programFile + "\\RadView\\WebLOAD";
				// listener.getLogger().println("WebLOAD Installation path not
				// specified, guessed:" + path);
			}
		}
        //File binDir = new File(path,"bin");
        String execName = "WLAnalyticsCmd.exe";
        String analyticsExecPath = path + "\\bin\\" + execName;
        
        FilePath webloadFile = new FilePath(launcher.getChannel(), analyticsExecPath);
        if (!webloadFile.exists()) {
            throw new AbortException("Can't find installation at " + analyticsExecPath);
        }
        
        String inputFsFileName = build.getEnvironment(listener).expand(inputLsFile); //e.g expand 'ls${BUILD_NUMBER}.ls' to 'ls1.ls'
        FilePath lsFilePath = new FilePath (build.getWorkspace(), inputFsFileName);
        if (!lsFilePath.exists()) {
            throw new AbortException("Can't find Load Session file " + lsFilePath);
        }
        List<FilePath> sessionsToCompare = new ArrayList<FilePath>();
        
        if (compareToSessions != null && !compareToSessions.isEmpty()) {
            String[] splitSessions = compareToSessions.split(",");
            for (String s : splitSessions) {
                FilePath compareSessionFilePath = new FilePath(build.getWorkspace(), build.getEnvironment(listener).expand(s));
				sessionsToCompare.add(compareSessionFilePath);
                if (compareSessionFilePath.exists()) {
                    listener.getLogger().println("Add LS for comparison " + compareSessionFilePath);
                } else {
                    listener.getLogger().println("WARN : LS for comparison not found " + compareSessionFilePath);
                }
            }
        }
        
        Run previousBuild = build.getPreviousBuild();
        int prevBuilds = compareToPreviousBuilds;
        while ((prevBuilds > 0) && previousBuild != null) {
            File artifactsDir = previousBuild.getArtifactsDir();
            String previousLsName = previousBuild.getEnvironment(listener).expand(inputLsFile);
            listener.getLogger().println("Looking for " + previousLsName);
            File previousLsFile = new File(artifactsDir, previousLsName);
            FilePath previousLsFilePath = new FilePath(build.getWorkspace(), previousLsFile.getPath());
            if (previousLsFilePath.exists()) {
                sessionsToCompare.add(previousLsFilePath);
                listener.getLogger().println("Add previous LS for comparison from artifacts " + previousLsFile.getAbsolutePath());
            } else {
                previousLsFilePath = new FilePath(build.getWorkspace(), previousLsName);
                if (previousLsFilePath.exists()) {
                    sessionsToCompare.add(previousLsFilePath);
                    listener.getLogger().println("Add previous LS for comparison from workspace " + previousLsFilePath);
                } else {
                    listener.getLogger().println("Not found previous LS from build " + previousBuild);
                }
            }
            previousBuild = previousBuild.getPreviousBuild();
            prevBuilds--;
        }
        
        args.add(analyticsExecPath);
        args.add("-m");
        args.add("U"); //pUblish
        args.add("-p");
        if (portfolioFile != null && !portfolioFile.isEmpty()) {
            args.add(portfolioFile);
        } else {
            args.add("Summary Portfolio");
        }
        args.add("-ls");
        args.add(lsFilePath);
        for (FilePath compareSess : sessionsToCompare) {
            args.add("-ls");
            args.add(compareSess);
        }
        
        if (format != null ) {
            args.add("-f");
            args.add(format.name());
        }
        args.add("-l"); //location
        if (location != null && !location.isEmpty()) {
            args.add(location);
        } else {
            args.add(build.getWorkspace()); //default location will be the workspace
        }
        if (reportName != null && !reportName.isEmpty()) {
            args.add("-n");
            args.add(reportName);
        }
        
        ArgumentListBuilder winCmd = args.toWindowsCommand();

        listener.getLogger().println("Executing the command " + winCmd.toStringWithQuote());
        int result = launcher.launch().cmds(winCmd).stdout(listener).envs(envVars).join();
        listener.getLogger().println("Execution ended");
        
        return (result==0);
    }

    @Override
    public AnalyticsDescriptor getDescriptor() {
        return (AnalyticsDescriptor)super.getDescriptor();
    }

    @Extension
    public static final class AnalyticsDescriptor extends BuildStepDescriptor<Builder> {
//        @CopyOnWrite
//        private volatile MsBuildInstallation[] installations = new MsBuildInstallation[0];
        
        private String webloadInstallationPath;

        public AnalyticsDescriptor() {
            super(WebLoadAnalyticsBuilder.class);
            load();
        }

        public String getDisplayName() {
            return "Generate WebLOAD Analytics Report";
        }
        
        public FormValidation doCheckInputLsFile(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please select a load session file");
            if (!value.endsWith(".ls"))
                return FormValidation.warning("File extension is not .ls, is this the right file?");
            return FormValidation.ok();
        }
        
        public ComboBoxModel doFillPortfolioFileItems() {
            return new ComboBoxModel("Summray Portfolio", "Session Comparison Portfolio", "Extended Summary Portfolio"); 
        }
        
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            // To persist global configuration information,
            // set that to properties and call save().
        	formData = formData.getJSONObject("webload");
            webloadInstallationPath = formData.getString("webloadInstallationPath");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }
        
        public String getWebloadInstallationPath() {
            return webloadInstallationPath;
        }


//        public MsBuildInstallation[] getInstallations() {
  //          return installations;
    //    }

      //  public void setInstallations(MsBuildInstallation... antInstallations) {
//            this.installations = antInstallations;
  //          save();
    //    }

//        public MsBuildInstallation.DescriptorImpl getToolDescriptor() {
  //          return ToolInstallation.all().get(MsBuildInstallation.DescriptorImpl.class);
    //    }
    }
}
