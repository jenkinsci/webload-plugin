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
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author yams
 */
public class WebLoadConsoleBuilder extends Builder {
    private final String tplFile;
    private final String lsFile;
    private final long executionDuration;
    private final long virtualClients;
    private final long probindClient;
    
    private static final String[] sessionExtensions = 
        { "ls", "dat", "isd", "mdb", "sdb" };

    public String getTplFile() {
        return tplFile;
    }

    public String getLsFile() {
        return lsFile;
    }

    public long getExecutionDuration() {
        return executionDuration;
    }

    public long getVirtualClients() {
        return virtualClients;
    }

    public long getProbindClient() {
        return probindClient;
    }

    
    @DataBoundConstructor
    public WebLoadConsoleBuilder(String tplFile, String lsFile, long executionDuration, long virtualClients, long probindClient) {
        this.tplFile = tplFile;
        this.lsFile = lsFile;
        this.executionDuration = executionDuration;
        this.virtualClients = virtualClients;
        this.probindClient = probindClient;
    }
    
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }
   
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars envVars = build.getEnvironment(listener);
        
                // on Windows environment variables are converted to all upper case,
                // but no such conversions are done on Unix, so to make this cross-platform,
                // convert variables to all upper cases.
                for(Map.Entry<String,String> e : build.getBuildVariables().entrySet())
                    envVars.put(e.getKey(),e.getValue());

        String path = envVars.get("WL_HOME");
        if ((path!=null) && path.length()>0) {
        	listener.getLogger().println("Using WL_HOME:" + path);
        } else {
            path = getDescriptor().getWebloadInstallationPath();       	
            if ((path!=null) && path.length()>0) {
            	listener.getLogger().println("Using WebloadInstallationPath:" + path);
            } else {
	            String programFile = envVars.get("ProgramFiles(x86)");
	            if (programFile==null) {
	                programFile = envVars.get("ProgramFiles");
	            }
	            if (programFile==null) {
	                programFile = "C:\\Program Files";
	            }
	            path = programFile + "\\RadView\\WebLOAD";
	            //listener.getLogger().println("WebLOAD Installation path not specified, guessed:" + path);
            }
        }
        String execName = "webload.exe";
        
        String webloadExecPath = path + "\\bin\\" + execName;
        
        //FIXME:
        //FilePath webloadFile = new FilePath();
        //if (!webloadFile.exists()) {
         //   throw new AbortException("Can't find installation at " + webloadFile.getAbsolutePath());
        //}
        
        if (tplFile == null || tplFile.isEmpty()) {
            throw new AbortException("Template file not specified");
        }
        String lsFileName = lsFile;
        if (lsFileName == null || lsFileName.isEmpty()) {
            lsFileName = tplFile;
        }
        lsFileName = replaceExtension(lsFileName, "ls");
        lsFileName = envVars.expand(lsFileName);
        
        FilePath workspace = build.getWorkspace();
        if (workspace==null) {
            throw new AbortException("Unexpected error, workspace not configured");
        }
        
        FilePath lsFilePath = new FilePath(workspace, lsFileName);
        
        FilePath resultsFile;
        
        resultsFile = workspace.child("results.xml");
        if (resultsFile.exists()) {
            resultsFile.delete();
        }
        
        args.add(webloadExecPath); //webloadFile);
        args.add(tplFile);
        args.add(lsFilePath);
        if (virtualClients > 0) {
            args.add("/vc");
            args.add(virtualClients);
        }
        if (probindClient > 0) {
            args.add("/pc");
            args.add(probindClient);
        }
        args.add("/ar"); //Auto-run
        if (executionDuration > 0) {
            args.add(executionDuration);
        }
        args.add("/rc"); //Return-code
        args.add(resultsFile);
        ArgumentListBuilder winCmd = args; //.toWindowsCommand();
        
        listener.getLogger().println("Executing the command " + winCmd.toStringWithQuote());
        int result = launcher.launch().cmds(winCmd).stdout(listener).envs(envVars).join();
        listener.getLogger().println("Execution ended, parsing return code");
        
        if (!resultsFile.exists()) {
            throw new AbortException("WebLOADO session ended unexpectedely. Result file not created");
        }
        String r = resultsFile.readToString();
        listener.getLogger().println(r);
        
        //Using string manipulation instead of XML becuase version older than 10.1 had illegal xml file.
        String sessionReturnCode = extractValue("SessionReturnCode=\"","\"", r );
        String errorDescription = extractValue("ErrorDescription=\"","\"", r );
        listener.getLogger().println("SessionReturnCode " + sessionReturnCode);
        listener.getLogger().println("ErrorDescription " + errorDescription);
        
        if ( ! ("Passed".equalsIgnoreCase(sessionReturnCode)) ) {
            build.setResult(Result.UNSTABLE);
        }

        if (!lsFilePath.exists()) {
            throw new AbortException("WebLOADO session ended unexpectedely. Load Session file not created");
        }
        //TODO:if (archiveSessionFile)
        if (new File(lsFileName).isAbsolute()) {
        	listener.getLogger().println("Not archiving from absolute path");
        } else {
	        Map<String, String> files = new HashMap<String, String>();
	        for (String e : sessionExtensions) {
	            String f = replaceExtension(lsFileName, e);
	            f = f.replace(File.separatorChar, '/');
	            
	            listener.getLogger().println("Archiving " + f);
	            files.put(f, f);
	        }
	        try {
				build.pickArtifactManager().archive(workspace, launcher, listener, files);
			} catch (IOException e1) {
				listener.getLogger().println("Error archiving files: " + e1.getLocalizedMessage());
			}
        }
        
        return (result==0);
    }
    
    static String extractValue(String prefix, String suffix, String str) {
        Pattern p = Pattern.compile("(" + prefix + ")(.*)(" + suffix + ")");
        Matcher m = p.matcher(str);
        if (!m.find()) {
            return null;
        }
        
        if (m.groupCount() < 2) {
            return null;
        }
        return m.group(2);
    }
    
    @Override
    public ConsoleDescriptor getDescriptor() {
        return (ConsoleDescriptor)super.getDescriptor();
    }

    static String replaceExtension(String name, String extension) {
        int ext = name.lastIndexOf(".");
        if (ext>0) {
            return name.substring(0, ext) + "." + extension;
        } else {
            return name + "." + extension;
        }
    }

    @Extension
    public static final class ConsoleDescriptor extends BuildStepDescriptor<Builder> {
//        @CopyOnWrite
//        private volatile MsBuildInstallation[] installations = new MsBuildInstallation[0];
        
        private String webloadInstallationPath;

        public ConsoleDescriptor() {
            super(WebLoadConsoleBuilder.class);
            load();
        }

        public String getDisplayName() {
            return "Execute WebLOAD load session";
        }
        
        public FormValidation doCheckTplFile(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please select a template file");
            if (!value.endsWith(".tpl"))
                return FormValidation.warning("File extension is not tpl, is this the right file?");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckExecutionDuration(@QueryParameter String value)
                throws IOException, ServletException {
            if ((value == null) || value.length()==0 ) {
                return FormValidation.ok();            
            }
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException nfe) {
                return FormValidation.error("Please select a number");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
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
