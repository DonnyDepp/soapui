/*
 *  soapUI, copyright (C) 2004-2008 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.wsdl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;

import org.apache.commons.ssl.OpenSSL;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.CredentialsConfig;
import com.eviware.soapui.config.InterfaceConfig;
import com.eviware.soapui.config.MockServiceConfig;
import com.eviware.soapui.config.OperationConfig;
import com.eviware.soapui.config.ProjectConfig;
import com.eviware.soapui.config.SoapuiProjectDocumentConfig;
import com.eviware.soapui.config.TestSuiteConfig;
import com.eviware.soapui.config.WsdlRequestConfig;
import com.eviware.soapui.config.impl.OperationConfigImpl;
import com.eviware.soapui.config.impl.WsdlInterfaceConfigImpl;
import com.eviware.soapui.impl.WorkspaceImpl;
import com.eviware.soapui.impl.support.AbstractInterface;
import com.eviware.soapui.impl.wsdl.endpoint.DefaultEndpointStrategy;
import com.eviware.soapui.impl.wsdl.mock.WsdlMockService;
import com.eviware.soapui.impl.wsdl.support.wsdl.UrlWsdlLoader;
import com.eviware.soapui.impl.wsdl.support.wss.DefaultWssContainer;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.iface.Operation;
import com.eviware.soapui.model.iface.Request;
import com.eviware.soapui.model.mock.MockService;
import com.eviware.soapui.model.project.EndpointStrategy;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.project.ProjectListener;
import com.eviware.soapui.model.propertyexpansion.DefaultPropertyExpansionContext;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansion;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionContainer;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionContext;
import com.eviware.soapui.model.settings.Settings;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.settings.ProjectSettings;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.settings.WsdlSettings;
import com.eviware.soapui.support.SoapUIException;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.Tools;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.scripting.SoapUIScriptEngine;
import com.eviware.soapui.support.scripting.SoapUIScriptEngineRegistry;

/**
 * WSDL project implementation
 * 
 * @author Ole.Matzura
 */

public class WsdlProject extends
		AbstractTestPropertyHolderWsdlModelItem<ProjectConfig> implements
		Project, PropertyExpansionContainer {
	public final static String AFTER_LOAD_SCRIPT_PROPERTY = WsdlProject.class
			.getName()
			+ "@setupScript";
	public final static String BEFORE_SAVE_SCRIPT_PROPERTY = WsdlProject.class
			.getName()
			+ "@tearDownScript";
	public final static String RESOURCE_ROOT_PROPERTY = WsdlProject.class
			.getName()
			+ "@resourceRoot";
	public final static String SHADOW_PASSWORD = WsdlProject.class.getName() + "@shadowPassword";

	private WorkspaceImpl workspace;
	private String path;
	private List<AbstractInterface<?>> interfaces = new ArrayList<AbstractInterface<?>>();
	private List<WsdlTestSuite> testSuites = new ArrayList<WsdlTestSuite>();
	private List<WsdlMockService> mockServices = new ArrayList<WsdlMockService>();
	private Set<ProjectListener> projectListeners = new HashSet<ProjectListener>();
	private SoapuiProjectDocumentConfig projectDocument;
	private ImageIcon disabledIcon;
	private ImageIcon closedIcon;
	private ImageIcon remoteIcon;
	private EndpointStrategy endpointStrategy = new DefaultEndpointStrategy();
	private long lastModified;
	private boolean remote;
	private boolean open = true;
	private boolean disabled;

	private SoapUIScriptEngine afterLoadScriptEngine;
	private SoapUIScriptEngine beforeSaveScriptEngine;
	private PropertyExpansionContext context = new DefaultPropertyExpansionContext(
			this);
	private DefaultWssContainer wssContainer;

	private final static Logger log = Logger.getLogger(WsdlProject.class);

	public WsdlProject() throws XmlException, IOException, SoapUIException {
		this((WorkspaceImpl) null);
	}

	public WsdlProject(String path) throws XmlException, IOException,
			SoapUIException {
		this(path, null);
	}

	public WsdlProject(WorkspaceImpl workspace) {
		this(null, workspace, true);
	}

	public WsdlProject(String path, WorkspaceImpl workspace) {
		this(path, workspace, true);
	}

	public WsdlProject(String path, WorkspaceImpl workspace, boolean create) {
		this(path, workspace, create, true, null);
	}

	public WsdlProject(String path, WorkspaceImpl workspace, boolean create,
			boolean open, String tempName) {
		super(null, workspace, "/project.gif");

		this.workspace = workspace;
		this.path = path;

		try {
			if (path != null && open) {
				File file = new File(path);
				if (file.exists()) {
					try {
						loadProject(file.toURI().toURL());
						lastModified = file.lastModified();
					} catch (MalformedURLException e) {
						SoapUI.logError(e);
						disabled = true;
					}
				} else {
					try {
						remote = true;
						loadProject(new URL(path));
					} catch (MalformedURLException e) {
						SoapUI.logError(e);
						disabled = true;
					}
				}
			}
		} catch (SoapUIException e) {
			SoapUI.logError(e);
			disabled = true;
		} finally {
			closedIcon = UISupport.createImageIcon("/closedProject.gif");
			remoteIcon = UISupport.createImageIcon("/remoteProject.gif");
			disabledIcon = UISupport.createImageIcon("/disabledProject.gif");

			this.open = open && !disabled;

			if (projectDocument == null) {
				projectDocument = SoapuiProjectDocumentConfig.Factory
						.newInstance();
				setConfig(projectDocument.addNewSoapuiProject());
				if (tempName != null || path != null)
					getConfig()
							.setName(
									StringUtils.isNullOrEmpty(tempName) ? getNameFromPath()
											: tempName);

				setPropertiesConfig(getConfig().addNewProperties());
				wssContainer = new DefaultWssContainer(this, getConfig()
						.addNewWssContainer());
				setResourceRoot("${projectDir}");
			}

			endpointStrategy.init(this);
			setProjectRoot(path);

			for (ProjectListener listener : SoapUI.getListenerRegistry()
					.getListeners(ProjectListener.class)) {
				addProjectListener(listener);
			}
		}
	}

	public boolean isRemote() {
		return remote;
	}

	private void loadProject(URL file) throws SoapUIException {
		try {
			UISupport.setHourglassCursor();

			UrlWsdlLoader loader = new UrlWsdlLoader(file.toString());
			loader.setUseWorker(false);
			projectDocument = SoapuiProjectDocumentConfig.Factory.parse(loader
					.load());

			// see if there is encoded data
			try {
				checkForEncodedData(projectDocument.getSoapuiProject());
			} catch (GeneralSecurityException e) {
				throw new SoapUIException("Error decrypting data", e);
			}
			
			setConfig(projectDocument.getSoapuiProject());

			// removed cached definitions if caching is disabled
			if (!getSettings().getBoolean(WsdlSettings.CACHE_WSDLS)) {
				removeDefinitionCaches(projectDocument);
			}

			log.info("Loaded project from [" + file.toString() + "]");

			List<InterfaceConfig> interfaceConfigs = getConfig()
					.getInterfaceList();
			for (InterfaceConfig config : interfaceConfigs) {
				AbstractInterface<?> iface = InterfaceFactoryRegistry.build(
						this, config);
				interfaces.add(iface);
			}

			List<TestSuiteConfig> testSuiteConfigs = getConfig()
					.getTestSuiteList();
			for (TestSuiteConfig config : testSuiteConfigs) {
				testSuites.add(new WsdlTestSuite(this, config));
			}

			List<MockServiceConfig> mockServiceConfigs = getConfig()
					.getMockServiceList();
			for (MockServiceConfig config : mockServiceConfigs) {
				mockServices.add(new WsdlMockService(this, config));
			}

			if (!getConfig().isSetWssContainer())
				getConfig().addNewWssContainer();

			wssContainer = new DefaultWssContainer(this, getConfig()
					.getWssContainer());

			endpointStrategy.init(this);

			if (!getConfig().isSetProperties())
				getConfig().addNewProperties();

			setPropertiesConfig(getConfig().getProperties());

			afterLoad();
		} catch (Exception e) {
			if (e instanceof XmlException) {
				XmlException xe = (XmlException) e;
				XmlError error = xe.getError();
				if (error != null)
					System.err.println("Error at line " + error.getLine()
							+ ", column " + error.getColumn());
			}

			e.printStackTrace();
			throw new SoapUIException("Failed to load project from file ["
					+ file.toString() + "]", e);
		} finally {
			UISupport.resetCursor();
		}
	}

	/**
	 * Decode encrypted data and restore user/pass
	 * 
	 * @author robert nemet
	 * @param soapuiProject
	 * @throws IOException 
	 * @throws GeneralSecurityException 
	 */
	private void checkForEncodedData(ProjectConfig soapuiProject) throws IOException, GeneralSecurityException {

		byte[] encryptedContent = soapuiProject.getEncryptedContent();
		String password = null;
		
		// no encrypted data then go back
		if (encryptedContent == null || encryptedContent.length < 1) {
			return;
		}
		
		try {
			byte[] data = OpenSSL.decrypt("des3", soapuiProject.getName().toCharArray(), encryptedContent);
			password = new String(data, "UTF8");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GeneralSecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for (InterfaceConfig pInterface : soapuiProject.getInterfaceList()) {
			for (OperationConfig operation :  ((WsdlInterfaceConfigImpl)pInterface).getOperationList()) {
				for (WsdlRequestConfig request : ((OperationConfigImpl) operation).getCallList()) {
					try {
						CredentialsConfig credentials = request.getCredentials();
						if( credentials == null ) {
							continue;
						}
						String usernameEncrypted = credentials.getUsername();
						String passwordEncrypted = credentials.getPassword();
						if(usernameEncrypted == null || passwordEncrypted == null ) {
							continue;
						}
						// encrypt username
						byte[] decryptedUsername = OpenSSL.decrypt("des3",password.toCharArray(), usernameEncrypted.getBytes("UTF8"));
						// encrypt password
						byte[] decryptedPassword = OpenSSL.decrypt("des3",password.toCharArray(), passwordEncrypted.getBytes("UTF8"));
						credentials.setUsername(new String(decryptedUsername, "UTF8"));
						credentials.setPassword(new String(decryptedPassword, "UTF8"));
					} catch (IOException e) {
						e.printStackTrace();
						log.error("Credentials decrypting error");
						throw e;
					} catch (GeneralSecurityException e) {
						e.printStackTrace();
						log.error("Credentials decrypting error");
						throw e;
					}
				}
			}
		}
		
	}

	@Override
	public void afterLoad() {
		super.afterLoad();

		try {
			runAfterLoadScript();
		} catch (Exception e) {
			SoapUI.logError(e);
		}
	}

	private void setProjectRoot(String path) {
		if (path != null && projectDocument != null) {
			int ix = path.lastIndexOf(File.separatorChar);
			if (ix > 0)
				getSettings().setString(ProjectSettings.PROJECT_ROOT,
						path.substring(0, ix));
		}
	}

	public void setResourceRoot(String resourceRoot) {
		String old = getResourceRoot();

		getConfig().setResourceRoot(resourceRoot);
		notifyPropertyChanged(RESOURCE_ROOT_PROPERTY, old, resourceRoot);
	}

	public String getResourceRoot() {
		if (!getConfig().isSetResourceRoot())
			getConfig().setResourceRoot("");

		return getConfig().getResourceRoot();
	}

	@Override
	public ImageIcon getIcon() {
		if (isDisabled())
			return disabledIcon;
		else if (!isOpen())
			return closedIcon;
		else if (isRemote())
			return remoteIcon;
		else
			return super.getIcon();
	}

	private String getNameFromPath() {
		int ix = path.lastIndexOf(isRemote() ? '/' : File.separatorChar);
		String name = ix == -1 ? path : path.substring(ix + 1);
		return name;
	}

	@Override
	public String getDescription() {
		if (isOpen())
			return super.getDescription();

		String name = getName();

		if (isDisabled())
			name += " - disabled";
		else
			name += " - closed";

		return name;
	}

	public WorkspaceImpl getWorkspace() {
		return workspace;
	}

	public AbstractInterface<?> getInterfaceAt(int index) {
		return interfaces.get(index);
	}

	public AbstractInterface<?> getInterfaceByName(String interfaceName) {
		return (AbstractInterface<?>) getWsdlModelItemByName(interfaces,
				interfaceName);
	}

	public AbstractInterface<?> getInterfaceByTechnicalId(String technicalId) {
		for (int c = 0; c < getInterfaceCount(); c++) {
			if (getInterfaceAt(c).getTechnicalId().equals(technicalId))
				return getInterfaceAt(c);
		}

		return null;
	}

	public int getInterfaceCount() {
		return interfaces.size();
	}

	public String getPath() {
		return path;
	}

	public boolean save() throws IOException {
		if (!isOpen() || isDisabled() || isRemote())
			return true;

// check for encryption
		String passwordForEncryption = getSettings().getString(ProjectSettings.SHADOW_PASSWORD, null);
		if (passwordForEncryption != null) {
			if (passwordForEncryption.length() > 1) {
				// we have password so do encryption
				try {
					encryptData(passwordForEncryption);
					projectDocument.getSoapuiProject().setEncryptedContent(OpenSSL.encrypt("des3", getName().toCharArray(),passwordForEncryption.getBytes()));
				} catch (GeneralSecurityException e) {
					UISupport.showErrorMessage("Encryption Error");
				}
			} else {
				// no password no encryption.
				projectDocument.getSoapuiProject().setEncryptedContent(null);
			}
		} else {
			// no password no encryption.
			projectDocument.getSoapuiProject().setEncryptedContent(null);
		}
// end of encryption.
		
		if (path == null || isRemote()) {
			path = getName() + "-soapui-project.xml";
			File file = null;

			while (file == null
					|| (file.exists() && !UISupport.confirm("File ["
							+ file.getName() + "] exists, overwrite?",
							"Overwrite File?"))) {
				file = UISupport.getFileDialogs().saveAs(this,
						"Save project " + getName(), ".xml",
						"XML Files (*.xml)", new File(path));
				if (file == null)
					return false;
			}

			path = file.getAbsolutePath();
		}

		File projectFile = new File(path);

		while (projectFile.exists() && !projectFile.canWrite()) {
			if (UISupport.confirm("Project file [" + path
					+ "] can not be written to, save to new file?",
					"Save Project")) {
				projectFile = UISupport.getFileDialogs().saveAs(this,
						"Save project " + getName(), ".xml",
						"XML Files (*.xml)", projectFile);

				if (projectFile == null)
					return false;

				path = projectFile.getAbsolutePath();
			} else
				return false;
		}

		// check modified
		if (projectFile.exists() && lastModified != 0
				&& lastModified < projectFile.lastModified()) {
			if (!UISupport.confirm("Project file for [" + getName()
					+ "] has been modified externally, overwrite?",
					"Save Project"))
				return false;
		}

		if (projectFile.exists()
				&& getSettings().getBoolean(UISettings.CREATE_BACKUP)) {
			createBackup(projectFile);
		}

		return saveIn(projectFile);
	}

	/**
	 * Encrypt credentials(user,pass) and storing data in ecryptedContent. Using base64 for encryption.
	 * 
	 * @author robert nemet
	 * @param passwordForEncryption
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	private void encryptData(String passwordForEncryption) throws IOException,
			GeneralSecurityException {

		log.info("Encrypting credentials.");
		// encrypt credentials
		for (Interface pInterface : getInterfaceList()) {
			for (Operation operation : ((WsdlInterface) pInterface)
					.getOperationList()) {
				for (Request request : ((WsdlOperation) operation)
						.getRequestList()) {
					try {
						String username = ((WsdlRequest) request).getUsername();
						String password = ((WsdlRequest) request).getPassword();
						if(username == null || password == null ) {
							continue;
						}
						// encrypt username
						byte[] encryptedUsername = OpenSSL.encrypt("des3",passwordForEncryption.toCharArray(), username.getBytes("UTF8"), true);
						// encrypt password
						byte[] encryptedPassword = OpenSSL.encrypt("des3",passwordForEncryption.toCharArray(), password.getBytes("UTF8"), true);
						((WsdlRequest) request).setUsername(new String(encryptedUsername, "UTF8"));
						((WsdlRequest) request).setPassword(new String(encryptedPassword, "UTF8"));
					} catch (IOException e) {
						e.printStackTrace();
						log.error("Credentials encrypting error");
						throw e;
					} catch (GeneralSecurityException e) {
						e.printStackTrace();
						log.error("Credentials encrypting error");
						throw e;
					}
				}
			}
		}
	}

	public boolean saveBackup() throws IOException {
		File projectFile;
		if (path == null || isRemote()) {
			projectFile = new File(getName() + "-soapui-project.xml");
		} else {
			projectFile = new File(path);
		}
		File backupFile = getBackupFile(projectFile);
		return saveIn(backupFile);
	}

	private boolean saveIn(File projectFile) throws IOException {
		long size = 0;

		beforeSave();

		XmlOptions options = new XmlOptions();
		if (SoapUI.getSettings().getBoolean(
				WsdlSettings.PRETTY_PRINT_PROJECT_FILES))
			options.setSavePrettyPrint();

		// check for caching
		if (!getSettings().getBoolean(WsdlSettings.CACHE_WSDLS)) {
			// no caching -> create copy and remove definition cachings
			SoapuiProjectDocumentConfig config = (SoapuiProjectDocumentConfig) projectDocument
					.copy();
			removeDefinitionCaches(config);

			config.getSoapuiProject().setSoapuiVersion(SoapUI.SOAPUI_VERSION);
			config.save(projectFile, options);
		} else {
			try {
				// save to temporary buffer to avoid corruption of file
				projectDocument.getSoapuiProject().setSoapuiVersion(
						SoapUI.SOAPUI_VERSION);
				ByteArrayOutputStream writer = new ByteArrayOutputStream(8192);
				projectDocument.save(writer, options);
				FileOutputStream out = new FileOutputStream(projectFile);
				writer.writeTo(out);
				out.close();
				size = writer.size();
			} catch (Throwable t) {
				SoapUI.logError(t);
				UISupport.showErrorMessage("Failed to save project ["
						+ getName() + "]: " + t.toString());
				return false;
			}
		}

		lastModified = projectFile.lastModified();
		log.info("Saved project [" + getName() + "] to ["
				+ projectFile.getAbsolutePath() + " - " + size + " bytes");
		setProjectRoot(path);
		return true;
	}

	@Override
	public void beforeSave() {
		try {
			runBeforeSaveScript();
		} catch (Exception e) {
			SoapUI.logError(e);
		}

		// notify
		for (AbstractInterface<?> iface : interfaces)
			iface.beforeSave();

		for (WsdlTestSuite testSuite : testSuites)
			testSuite.beforeSave();

		for (WsdlMockService mockService : mockServices)
			mockService.beforeSave();

		endpointStrategy.onSave();
	}

	private void createBackup(File projectFile) throws IOException {
		File backupFile = getBackupFile(projectFile);
		log.info("Backing up [" + projectFile + "] to [" + backupFile + "]");
		Tools.copyFile(projectFile, backupFile, true);
	}

	private File getBackupFile(File projectFile) {
		String backupFolderName = getSettings().getString(
				UISettings.BACKUP_FOLDER, "");

		File backupFolder = new File(backupFolderName);
		if (!backupFolder.isAbsolute()) {
			backupFolder = new File(projectFile.getParentFile(),
					backupFolderName);
		}

		if (!backupFolder.exists())
			backupFolder.mkdirs();

		File backupFile = new File(backupFolder, projectFile.getName()
				+ ".backup");
		return backupFile;
	}

	private void removeDefinitionCaches(SoapuiProjectDocumentConfig config) {
		for (InterfaceConfig ifaceConfig : config.getSoapuiProject()
				.getInterfaceList()) {
			if (ifaceConfig.isSetDefinitionCache()) {
				log.info("Removing definition cache from interface ["
						+ ifaceConfig.getName() + "]");
				ifaceConfig.unsetDefinitionCache();
			}
		}
	}

	public AbstractInterface<?> addNewInterface(String name, String type) {
		AbstractInterface<?> iface = (AbstractInterface<?>) InterfaceFactoryRegistry
				.createNew(this, type, name);
		if (iface != null) {
			iface.getConfig().setType(type);

			interfaces.add(iface);
			fireInterfaceAdded(iface);
		}

		return iface;
	}

	public void addProjectListener(ProjectListener listener) {
		projectListeners.add(listener);
	}

	public void removeProjectListener(ProjectListener listener) {
		projectListeners.remove(listener);
	}

	public void fireInterfaceAdded(AbstractInterface<?> iface) {
		ProjectListener[] a = projectListeners
				.toArray(new ProjectListener[projectListeners.size()]);

		for (int c = 0; c < a.length; c++) {
			a[c].interfaceAdded(iface);
		}
	}

	public void fireInterfaceRemoved(AbstractInterface<?> iface) {
		ProjectListener[] a = projectListeners
				.toArray(new ProjectListener[projectListeners.size()]);

		for (int c = 0; c < a.length; c++) {
			a[c].interfaceRemoved(iface);
		}
	}

	public void fireInterfaceUpdated(AbstractInterface<?> iface) {
		ProjectListener[] a = projectListeners
				.toArray(new ProjectListener[projectListeners.size()]);

		for (int c = 0; c < a.length; c++) {
			a[c].interfaceUpdated(iface);
		}
	}

	public void fireTestSuiteAdded(WsdlTestSuite testSuite) {
		ProjectListener[] a = projectListeners
				.toArray(new ProjectListener[projectListeners.size()]);

		for (int c = 0; c < a.length; c++) {
			a[c].testSuiteAdded(testSuite);
		}
	}

	public void fireTestSuiteRemoved(WsdlTestSuite testSuite) {
		ProjectListener[] a = projectListeners
				.toArray(new ProjectListener[projectListeners.size()]);

		for (int c = 0; c < a.length; c++) {
			a[c].testSuiteRemoved(testSuite);
		}
	}

	public void fireMockServiceAdded(WsdlMockService mockService) {
		ProjectListener[] a = projectListeners
				.toArray(new ProjectListener[projectListeners.size()]);

		for (int c = 0; c < a.length; c++) {
			a[c].mockServiceAdded(mockService);
		}
	}

	public void fireMockServiceRemoved(WsdlMockService mockService) {
		ProjectListener[] a = projectListeners
				.toArray(new ProjectListener[projectListeners.size()]);

		for (int c = 0; c < a.length; c++) {
			a[c].mockServiceRemoved(mockService);
		}
	}

	public void removeInterface(AbstractInterface<?> iface) {
		int ix = interfaces.indexOf(iface);
		interfaces.remove(ix);
		try {
			fireInterfaceRemoved(iface);
		} finally {
			iface.release();
			getConfig().removeInterface(ix);
		}
	}

	public void removeTestSuite(WsdlTestSuite testSuite) {
		int ix = testSuites.indexOf(testSuite);
		testSuites.remove(ix);

		try {
			fireTestSuiteRemoved(testSuite);
		} finally {
			testSuite.release();
			getConfig().removeTestSuite(ix);
		}
	}

	public boolean isDisabled() {
		return disabled;
	}

	public int getTestSuiteCount() {
		return testSuites.size();
	}

	public WsdlTestSuite getTestSuiteAt(int index) {
		return testSuites.get(index);
	}

	public WsdlTestSuite getTestSuiteByName(String testSuiteName) {
		return (WsdlTestSuite) getWsdlModelItemByName(testSuites, testSuiteName);
	}

	public WsdlTestSuite addNewTestSuite(String name) {
		WsdlTestSuite testSuite = new WsdlTestSuite(this, getConfig()
				.addNewTestSuite());
		testSuite.setName(name);
		testSuites.add(testSuite);
		fireTestSuiteAdded(testSuite);

		return testSuite;
	}

	public boolean isCacheDefinitions() {
		return getSettings().getBoolean(WsdlSettings.CACHE_WSDLS);
	}

	public void setCacheDefinitions(boolean cacheDefinitions) {
		getSettings().setBoolean(WsdlSettings.CACHE_WSDLS, cacheDefinitions);
	}

	public boolean saveAs(String fileName) throws IOException {
		if (!isOpen() || isDisabled())
			return false;

		String oldPath = path;
		path = fileName;
		boolean result = save();
		if (!result)
			path = oldPath;
		else
			remote = false;

		setProjectRoot(path);

		return result;
	}

	@Override
	public void release() {
		super.release();

		if (isOpen()) {
			endpointStrategy.release();

			for (WsdlTestSuite testSuite : testSuites)
				testSuite.release();

			for (WsdlMockService mockService : mockServices)
				mockService.release();

			for (AbstractInterface<?> iface : interfaces)
				iface.release();
		}

		projectListeners.clear();

		if (afterLoadScriptEngine != null)
			afterLoadScriptEngine.release();

		if (beforeSaveScriptEngine != null)
			beforeSaveScriptEngine.release();
	}

	public WsdlMockService addNewMockService(String name) {
		WsdlMockService mockService = new WsdlMockService(this, getConfig()
				.addNewMockService());
		mockService.setName(name);
		mockServices.add(mockService);
		fireMockServiceAdded(mockService);

		return mockService;
	}

	public WsdlMockService getMockServiceAt(int index) {
		return mockServices.get(index);
	}

	public WsdlMockService getMockServiceByName(String mockServiceName) {
		return (WsdlMockService) getWsdlModelItemByName(mockServices,
				mockServiceName);
	}

	public int getMockServiceCount() {
		return mockServices.size();
	}

	public void removeMockService(WsdlMockService mockService) {
		int ix = mockServices.indexOf(mockService);
		mockServices.remove(ix);

		try {
			fireMockServiceRemoved(mockService);
		} finally {
			mockService.release();
			getConfig().removeMockService(ix);
		}
	}

	public List<TestSuite> getTestSuiteList() {
		return new ArrayList<TestSuite>(testSuites);
	}

	public List<MockService> getMockServiceList() {
		return new ArrayList<MockService>(mockServices);
	}

	public List<Interface> getInterfaceList() {
		return new ArrayList<Interface>(interfaces);
	}

	public Map<String, Interface> getInterfaces() {
		Map<String, Interface> result = new HashMap<String, Interface>();
		for (Interface iface : interfaces)
			result.put(iface.getName(), iface);

		return result;
	}

	public Map<String, TestSuite> getTestSuites() {
		Map<String, TestSuite> result = new HashMap<String, TestSuite>();
		for (TestSuite iface : testSuites)
			result.put(iface.getName(), iface);

		return result;
	}

	public Map<String, MockService> getMockServices() {
		Map<String, MockService> result = new HashMap<String, MockService>();
		for (MockService mockService : mockServices)
			result.put(mockService.getName(), mockService);

		return result;
	}

	public void reload() throws SoapUIException {
		reload(path);
	}

	public void reload(String path) throws SoapUIException {
		this.path = path;
		getWorkspace().reloadProject(this);
	}

	public boolean hasNature(String natureId) {
		Settings projectSettings = getSettings();
		String projectNature = projectSettings.getString(
				ProjectSettings.PROJECT_NATURE, null);
		return natureId.equals(projectNature);
	}

	public AbstractInterface<?> importInterface(AbstractInterface<?> iface,
			boolean importEndpoints, boolean createCopy) {
		iface.beforeSave();

		InterfaceConfig ifaceConfig = (InterfaceConfig) iface.getConfig()
				.copy();
		AbstractInterface<?> imported = InterfaceFactoryRegistry.build(this,
				ifaceConfig);
		if (ifaceConfig.isSetId() && createCopy)
			ifaceConfig.unsetId();

		interfaces.add(imported);

		if (iface.getProject() != this && importEndpoints) {
			endpointStrategy.importEndpoints(iface);
		}

		imported.afterLoad();
		fireInterfaceAdded(imported);

		return imported;
	}

	public WsdlTestSuite importTestSuite(WsdlTestSuite testSuite, String name,
			boolean createCopy) {
		testSuite.beforeSave();
		TestSuiteConfig testSuiteConfig = (TestSuiteConfig) getConfig()
				.addNewTestSuite().set(testSuite.getConfig().copy());
		testSuiteConfig.setName(name);
		if (testSuiteConfig.isSetId() && createCopy)
			testSuiteConfig.unsetId();

		testSuite = new WsdlTestSuite(this, testSuiteConfig);
		testSuites.add(testSuite);
		testSuite.afterLoad();
		fireTestSuiteAdded(testSuite);

		return testSuite;
	}

	public WsdlMockService importMockService(WsdlMockService mockService,
			String name, boolean createCopy) {
		mockService.beforeSave();
		MockServiceConfig mockServiceConfig = (MockServiceConfig) getConfig()
				.addNewMockService().set(mockService.getConfig().copy());
		mockServiceConfig.setName(name);
		if (mockServiceConfig.isSetId() && createCopy)
			mockServiceConfig.unsetId();
		mockService = new WsdlMockService(this, mockServiceConfig);
		mockServices.add(mockService);
		mockService.afterLoad();

		fireMockServiceAdded(mockService);

		return mockService;
	}

	public EndpointStrategy getEndpointStrategy() {
		return endpointStrategy;
	}

	public boolean isOpen() {
		return open;
	}

	public List<? extends ModelItem> getChildren() {
		ArrayList<ModelItem> list = new ArrayList<ModelItem>();
		list.addAll(getInterfaceList());
		list.addAll(getTestSuiteList());
		list.addAll(getMockServiceList());
		return list;
	}

	public void setAfterLoadScript(String script) {
		String oldScript = getAfterLoadScript();

		if (!getConfig().isSetAfterLoadScript())
			getConfig().addNewAfterLoadScript();

		getConfig().getAfterLoadScript().setStringValue(script);
		if (afterLoadScriptEngine != null)
			afterLoadScriptEngine.setScript(script);

		notifyPropertyChanged(AFTER_LOAD_SCRIPT_PROPERTY, oldScript, script);
	}

	public String getAfterLoadScript() {
		return getConfig().isSetAfterLoadScript() ? getConfig()
				.getAfterLoadScript().getStringValue() : null;
	}

	public void setBeforeSaveScript(String script) {
		String oldScript = getBeforeSaveScript();

		if (!getConfig().isSetBeforeSaveScript())
			getConfig().addNewBeforeSaveScript();

		getConfig().getBeforeSaveScript().setStringValue(script);
		if (beforeSaveScriptEngine != null)
			beforeSaveScriptEngine.setScript(script);

		notifyPropertyChanged(BEFORE_SAVE_SCRIPT_PROPERTY, oldScript, script);
	}

	public String getBeforeSaveScript() {
		return getConfig().isSetBeforeSaveScript() ? getConfig()
				.getBeforeSaveScript().getStringValue() : null;
	}

	public Object runAfterLoadScript() throws Exception {
		String script = getAfterLoadScript();
		if (StringUtils.isNullOrEmpty(script))
			return null;

		if (afterLoadScriptEngine == null) {
			afterLoadScriptEngine = SoapUIScriptEngineRegistry.create(
					SoapUIScriptEngineRegistry.GROOVY_ID, this);
			afterLoadScriptEngine.setScript(script);
		}

		afterLoadScriptEngine.setVariable("context", context);
		afterLoadScriptEngine.setVariable("project", this);
		afterLoadScriptEngine.setVariable("log", SoapUI.ensureGroovyLog());
		return afterLoadScriptEngine.run();
	}

	public Object runBeforeSaveScript() throws Exception {
		String script = getBeforeSaveScript();
		if (StringUtils.isNullOrEmpty(script))
			return null;

		if (beforeSaveScriptEngine == null) {
			beforeSaveScriptEngine = SoapUIScriptEngineRegistry.create(
					SoapUIScriptEngineRegistry.GROOVY_ID, this);
			beforeSaveScriptEngine.setScript(script);
		}

		beforeSaveScriptEngine.setVariable("context", context);
		beforeSaveScriptEngine.setVariable("project", this);
		beforeSaveScriptEngine.setVariable("log", SoapUI.ensureGroovyLog());
		return beforeSaveScriptEngine.run();
	}

	public PropertyExpansionContext getContext() {
		return context;
	}

	public DefaultWssContainer getWssContainer() {
		return wssContainer;
	}

	@Override
	public void resolve(ResolveContext context) {
		super.resolve(context);

		wssContainer.resolve(context);
	}

	public PropertyExpansion[] getPropertyExpansions() {
		return wssContainer.getPropertyExpansions();
	}

	public String getPropertiesLabel() {
		return "Custom Properties";
	}

	@Override
	public String getShadowPassword() {
		return getSettings().getString(ProjectSettings.SHADOW_PASSWORD, null);
	}

	@Override
	public void setShadowPassword(String password) {
		getSettings().setString(ProjectSettings.SHADOW_PASSWORD, password);
	}
	
	
}