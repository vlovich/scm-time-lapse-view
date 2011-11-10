package com.jonathanaquino.svntimelapseview;

import jargs.gnu.CmdLineParser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;

import com.jonathanaquino.svntimelapseview.helpers.DiffHelper;
import com.jonathanaquino.svntimelapseview.scm.ScmFactory;
import com.jonathanaquino.svntimelapseview.scm.ScmLoader;

/**
 * The top-level object in the program.
 */
public class Application {

    /**
     * Launches the application.
     */
    public static void main(String[] args) throws Exception {
        initializeLookAndFeel();
        CmdLineParser parser = new CmdLineParser();
        CmdLineParser.Option repositoryType = parser.addStringOption("scm");
        CmdLineParser.Option usernameOption = parser.addStringOption("username");
        CmdLineParser.Option passwordOption = parser.addStringOption("password");
        CmdLineParser.Option configOption = parser.addStringOption("config");
        CmdLineParser.Option limitOption = parser.addStringOption("limit");
        parser.parse(args);
        String filePathOrUrl = parser.getRemainingArgs().length > 0 ? parser.getRemainingArgs()[0] : null;
        String configFilePath = (String) parser.getOptionValue(configOption);
        String repositoryTypeName = (String) parser.getOptionValue(repositoryType, "git");
        ScmLoader loader;
        try {
        	loader = ScmFactory.create(repositoryTypeName);
        } catch (Exception e) {
        	System.err.println("Invalid repository type " + repositoryTypeName);
        	System.exit(1);
        	return;
        }
        
        if (configFilePath == null) { configFilePath = FileSystemView.getFileSystemView().getDefaultDirectory() + File.separator + "svn_time_lapse_view.ini"; }
        String username = (String) parser.getOptionValue(usernameOption);
        if (username == null) { username = ""; }
        String password = (String) parser.getOptionValue(passwordOption);
        if (password == null) { password = ""; }
        String limitString = (String) parser.getOptionValue(limitOption);
        int limit = limitString == null ? 100 : Integer.parseInt(limitString);
        new ApplicationWindow(new Application(new Configuration(configFilePath), loader), filePathOrUrl, username, password, limit).setVisible(true);
    }


    /**
     * Sets the appearance of window controls. Call this as early as possible.
     */
    private static void initializeLookAndFeel() throws Exception {
        //Apple stuff from Raj Singh [Jon Aquino 2007-10-14]
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.showGrowBox", "true");
        if (UIManager.getLookAndFeel() != null && UIManager.getLookAndFeel().getClass().getName().equals(UIManager.getSystemLookAndFeelClassName())) {
            return;
        }
        String lookAndFeel = System.getProperty("swing.defaultlaf");
        if (lookAndFeel == null){ lookAndFeel = UIManager.getSystemLookAndFeelClassName(); }
        UIManager.setLookAndFeel(lookAndFeel);
    }

    /** Configuration properties */
    private Configuration configuration;
    
    /** The loader to use on the repository */
    private ScmLoader loader;

    /** Cache of revision Diffs, keyed by "revision-number-1, revision-number-2" */
    private Map diffCache = new HashMap();

    /** The Revisions for the file being examined. */
    private List revisions = new ArrayList();

    /**
     * Creates a new Application.
     *
     * @param configuration  configuration properties
     */
    public Application(Configuration configuration, ScmLoader loader) {
        this.configuration = configuration;
        this.loader = loader;
    }

    /**
     * Returns the configuration properties.
     *
     * @return  the application settings
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Returns the set of differences between the contents of the two revisions.
     *
     * @param a  the first revision to examine
     * @param b  the second revision to examine
     * @param showDifferencesOnly  whether to hide identical lines
     * @return  a comparison of the lines in each revision
     */
    public Diff diff(Revision a, Revision b, boolean showDifferencesOnly) {
        String key = a.getRevisionNumber() + ", " + b.getRevisionNumber() + ", " + (showDifferencesOnly ? "differences only" : "all");
        if (! diffCache.containsKey(key)) {
            diffCache.put(key, DiffHelper.diff(a.getContents(), b.getContents(), showDifferencesOnly));
        }
        return (Diff) diffCache.get(key);
    }

    /**
     * Loads the revisions for the specified file.
     *
     * @param loader The loader to use (git/svn, etc) 
     * @param filePathOrUrl  Subversion URL or working-copy file path, git work-copy file path, etc
     * @param username  username, or an empty string for anonymous
     * @param password  password, or an empty string for anonymous
     * @param limit  maximum number of revisions to download
     * @param afterLoad  operation to run after the load finishes
     */
    public void load(final ScmLoader loader, String filePathOrUrl, String username, String password, int limit, final Closure afterLoad) throws Exception {
    	loader.setPassword(password);
    	loader.setUsername(username);
        loader.loadRevisions(filePathOrUrl, limit, new Closure() {
            public void execute() throws Exception {
                List revisions = loader.getRevisions();
                if (revisions.size() == 0) { throw new Exception("No revisions found"); }
                if (revisions.size() == 1) { throw new Exception("Only one revision found"); }
                Application.this.revisions = revisions;
                diffCache = new HashMap();
                afterLoad.execute();
            }
        });
    }

    /**
     * Returns the Revisions for the file being examined.
     *
     * @return  the file's revision history
     */
    public List getRevisions() {
        return revisions;
    }

    /**
     * Returns the Subversion revision loader.
     *
     * @return the object that downloads revisions
     */
    public ScmLoader getLoader() {
        return loader;
    }
}
