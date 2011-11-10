package com.jonathanaquino.svntimelapseview.scm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.jonathanaquino.svntimelapseview.Closure;
import com.jonathanaquino.svntimelapseview.Revision;
import com.jonathanaquino.svntimelapseview.helpers.MiscHelper;


/**
 * Loads revisions from a subversion repository.
 */
public class SvnLoader extends ScmLoader {
	public static final String KEY = "svn";
	
    /** Number of revisions downloaded for the current file. */
    private volatile int loadedCount = 0;

    /** Total number of revisions to download for the current file. */
    private volatile int totalCount = 0;

    /** The list of Revisions being downloaded. */
    private List revisions;

    /**
     * Builds a list of revisions for the given file.
     *
     * @param filePathOrUrl  Subversion URL or working-copy file path
     * @param username  username, or null for anonymous
     * @param password  password, or null for anonymous
     * @param limit  maximum number of revisions to download
     * @param afterLoad  operation to run after the load finishes
     */
    protected void loadRevisionsProper(String filePathOrUrl, int limit) throws Exception {
        try {
        	String username = getUsername();
        	String password = getPassword();
        	
            loadedCount = totalCount = 0;
            SVNURL fullUrl = svnUrl(filePathOrUrl, username, password);
            String url = fullUrl.removePathTail().toString();
            String filePath = fullUrl.getPath().replaceAll(".*/", "");
            SVNRepository repository = repository(url, username, password);
            List svnFileRevisions = new ArrayList(repository.getFileRevisions(filePath, null, 0, repository.getLatestRevision()));
            Collections.reverse(svnFileRevisions);
            List svnFileRevisionsToDownload = svnFileRevisions.size() > limit ? svnFileRevisions.subList(0, limit) : svnFileRevisions;
            totalCount = svnFileRevisionsToDownload.size();
            revisions = new ArrayList();
            for (Iterator i = svnFileRevisionsToDownload.iterator(); i.hasNext(); ) {
                SVNFileRevision r = (SVNFileRevision) i.next();
                if (isCancelled()) { break; }
                SVNProperties p = r.getRevisionProperties();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                repository.getFile(r.getPath(), r.getRevision(), null, outputStream);                
                String encoding = determineEncoding(outputStream.toByteArray());                
                String content = encoding == null ? outputStream.toString() : outputStream.toString(encoding); 
                revisions.add(new Revision(r.getRevision(), p.getStringValue(SVNRevisionProperty.AUTHOR), formatDate(p.getStringValue(SVNRevisionProperty.DATE)), p.getStringValue(SVNRevisionProperty.LOG), content));
                loadedCount++;
            }
            Collections.reverse(revisions);
        } finally {
            setLoading(false);
        }
    }

    /**
     * Normalizes the given file path or URL.
     *
     * @param filePathOrUrl  Subversion URL or working-copy file path
     * @param username  username, or null for anonymous
     * @param password  password, or null for anonymous
     * @return  the corresponding Subversion URL
     */
    private SVNURL svnUrl(String filePathOrUrl, String username, String password) throws SVNException {
        SVNURL svnUrl;
        if (new File(filePathOrUrl).exists()) {
            SVNClientManager clientManager = SVNClientManager.newInstance(SVNWCUtil.createDefaultOptions(true), username, password);
            svnUrl = clientManager.getWCClient().doInfo(new File(filePathOrUrl), SVNRevision.WORKING).getURL();
        } else {
            svnUrl = SVNURL.parseURIEncoded(filePathOrUrl);
        }
        return svnUrl;
    }

	/**
	 * Formats the value of the date property
	 *
	 * @param date  the revision date
	 * @return a friendlier date string
	 */
	protected String formatDate(String date) {
		return date.replaceFirst("(.*)T(.*:.*):.*", "$1 $2");
	}

    /**
     * Returns the specified Subversion repository
     *
     * @param url  URL of the Subversion repository or one of its files
     * @param username  username, or null for anonymous
     * @param password  password, or null for anonymous
     * @return  the repository handle
     */
    private SVNRepository repository(String url, String username, String password) throws Exception {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup(); /* svn:// and svn+xxx:// */
        FSRepositoryFactory.setup(); /* file:// */
        SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
        repository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager(username, password));
        repository.setTunnelProvider(SVNWCUtil.createDefaultOptions(true));
        return repository;
    }

    /**
     * Returns the number of revisions downloaded so far.
     *
     * @return  the number of revisions loaded in the current job.
     */
    public int getLoadedCount() {
        return loadedCount;
    }

    /**
     * Returns the total number of revisions that the current job is downloading.
     *
     * @return  the number of revisions being downloaded.
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * Returns the Revisions for the file being examined.
     *
     * @return  the file's revision history
     */
    public List getRevisions() {
        return revisions;
    }
}
