package org.joget.marketplace;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.io.FileUtils;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.datalist.model.DataListPluginExtend;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;

public class CompleteAssignmentDatalistAction extends DataListActionDefault implements DataListPluginExtend{
    private final static String MESSAGE_PATH = "messages/completeAssignmentDatalistAction";
 
    public String getName() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.CompleteAssignmentDatalistAction.name", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "7.0.2";
    }

    public String getClassName() {
        return getClass().getName();
    }
    
    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.CompleteAssignmentDatalistAction.menuName", getClassName(), MESSAGE_PATH);
    }
    
    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.CompleteAssignmentDatalistAction.desc", getClassName(), MESSAGE_PATH);
    }

    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion, appId, appVersion};
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/completeAssignmentDatalistAction.json", arguments, true, MESSAGE_PATH);
        
        return json;
    }

    public String getLinkLabel() {
        String label = getPropertyString("label");
        if (label == null || label.isEmpty()) {
            label = AppPluginUtil.getMessage("org.joget.marketplace.CompleteAssignmentDatalistAction.menuName", getClassName(), MESSAGE_PATH);
        }
        return label;
    }

    public String getHref() {
        return getPropertyString("href"); //Let system to handle to post to the same page
    }

    public String getTarget() {
        return "post";
    }

    public String getHrefParam() {
        return getPropertyString("hrefParam");  //Let system to set the parameter to the checkbox name
    }

    public String getHrefColumn() {
        return getPropertyString("hrefColumn"); //Let system to set the primary key column of the binder
    }

    public String getConfirmation() {
        return "Are you sure?";
    }

    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
        result.setUrl("REFERER");
        
        // only allow POST
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
        if (request != null && !"POST".equalsIgnoreCase(request.getMethod())) {
            return result;
        }
        
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        PackageDefinition packageDef = appDef.getPackageDefinition();
        
        String username = workflowUserManager.getCurrentUsername();
        
        // check for submited rows
        if (rowKeys != null && rowKeys.length > 0) {
            if (!getPropertyString("popupFormId").isEmpty()) {
                FormData formData = null;
                Form form = getForm(getPropertyString("popupFormId"));
                if (form != null && form.getStoreBinder() != null) {
                    String formDataJson = WorkflowUtil.getHttpServletRequest().getParameter("bulkcompleteformdata");
                    for (String processId : rowKeys) {
                        try {
                            WorkflowAssignment assignment = null; //workflowManager.getAssignmentByProcess(processId);
                            Collection<WorkflowAssignment> assignments;
                            
                            //check for matching activity ID
                            //assignments = workflowManager.getAssignmentListLite(packageDef.getId(), packageDef.getId() + "#" + packageDef.getVersion() + "#" + getPropertyString("processId"), processId, getPropertyString("activityDefId"), "dateCreated", true, 0, 1);
                            assignment = workflowManager.getAssignmentByRecordId(processId, packageDef.getId() + "#%#" + getPropertyString("processId"), getPropertyString("activityDefId"), username);
                            
                            if(assignment != null){
                                //matching assignment found
                                
                                //accept assignment
                                if (!assignment.isAccepted()) {
                                    workflowManager.assignmentAccept(assignment.getActivityId());
                                }
                                
                                //save form data
                                String recordId = appService.getOriginProcessId(processId);
                                formData = getFormData(formDataJson, recordId, assignment.getProcessId(), form);
                                if (formData != null) {
                                    formService.recursiveExecuteFormStoreBinders(form, form, formData);
                                }
                                
                                //complete assignment
                                workflowManager.assignmentComplete(assignment.getActivityId());
                                
                                LogUtil.info(getClassName(), "Assignment [" + processId + "] Completed.");
                            }else{
                                LogUtil.info(getClassName(), "Assignment [" + processId + "] Not Found.");
                            }
                        } catch (Exception e) {
                            LogUtil.error(getClassName(), e, "Assignment [" + processId + "] Failed to Complete.");
                        }
                    }
                    
                    if(formDataJson != null){
                        clearFiles(formDataJson);
                    }
                }
            }
        }
        
        return result;
    }
    
    public String getHTML(DataList dataList) {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        Map dataModel = new HashMap();
        dataModel.put("datalist", dataList);
        dataModel.put("element", this);
        dataModel.put("contextPath", WorkflowUtil.getHttpServletRequest().getContextPath());
        String rightToLeft = WorkflowUtil.getSystemSetupValue("rightToLeft");
        String locale = AppUtil.getAppLocale();
        dataModel.put("isRtl", Boolean.toString("true".equalsIgnoreCase(rightToLeft) || (locale != null && locale.startsWith("ar"))));
        
        Form form = CompleteAssignmentDatalistAction.getForm(getPropertyString("popupFormId"));
        if (form != null) {
            
            
            dataModel.put("buttonLabel", StringUtil.escapeString(ResourceBundleUtil.getMessage("form.button.submit"), StringUtil.TYPE_HTML, null));
            String json = StringUtil.escapeString(getSelectedFormJson(form), StringUtil.TYPE_HTML, null);
            dataModel.put("json", json);
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            dataModel.put("appDef", AppUtil.getCurrentAppDefinition());
            String nonceForm = SecurityUtil.generateNonce(new String[]{"EmbedForm", appDef.getAppId(), appDef.getVersion().toString(), json}, 1);
            dataModel.put("nonceForm", nonceForm);
            
            return pluginManager.getPluginFreeMarkerTemplate(dataModel, "org.joget.marketplace.CompleteAssignmentDatalistAction", "/templates/completeAssignmentDatalistActionPopUpForm.ftl", null);
        } else {
            return "";
        }
    }
    
    protected String getSelectedFormJson(Form form) {
        if (form != null) {
            FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
            String json = formService.generateElementJson(form);
            
            //replace the binder in json for popup form
            try {
                JSONObject temp = new JSONObject(json);
                JSONObject jsonProps = temp.getJSONObject(FormUtil.PROPERTY_PROPERTIES);
                
                JSONObject jsonLoadBinder = new JSONObject();
                jsonLoadBinder.put(FormUtil.PROPERTY_CLASS_NAME, "org.joget.plugin.enterprise.JsonFormBinder");
                jsonLoadBinder.put(FormUtil.PROPERTY_PROPERTIES, new JSONObject());
                jsonProps.put(FormBinder.FORM_LOAD_BINDER, jsonLoadBinder);
                jsonProps.put(FormBinder.FORM_STORE_BINDER, jsonLoadBinder);
                
                json = temp.toString();
            } catch (Exception e) {
                //ignore
            }
            
            return SecurityUtil.encrypt(json);
        }
        setProperty(FormUtil.PROPERTY_READONLY, "true");
        return "";
    }
    
    protected FormData getFormData(String json, String recordId, String processId, Form form) {
        try {
            FormData formData = new FormData();
            formData.setPrimaryKeyValue(recordId);
            formData.setProcessId(processId);

            FormRowSet rows = new FormRowSet();
            FormRow row = new FormRow();
            rows.add(row);

            JSONObject jsonObject = new JSONObject(json);
            for(Iterator iterator = jsonObject.keys(); iterator.hasNext();) {
                String key = (String) iterator.next();
                if (FormUtil.PROPERTY_TEMP_REQUEST_PARAMS.equals(key)) {
                    JSONObject tempRequestParamMap = jsonObject.getJSONObject(FormUtil.PROPERTY_TEMP_REQUEST_PARAMS);
                    JSONArray tempRequestParams = tempRequestParamMap.names();
                    if (tempRequestParams != null && tempRequestParams.length() > 0) {
                        for (int l = 0; l < tempRequestParams.length(); l++) {                        
                            List<String> rpValues = new ArrayList<String>();
                            String rpKey = tempRequestParams.getString(l);
                            JSONArray tempValues = tempRequestParamMap.getJSONArray(rpKey);
                            if (tempValues != null && tempValues.length() > 0) {
                                for (int m = 0; m < tempValues.length(); m++) {
                                    rpValues.add(tempValues.getString(m));
                                }
                            }
                            formData.addRequestParameterValues(rpKey, rpValues.toArray(new String[]{}));
                        }
                    }
                } else if (FormUtil.PROPERTY_TEMP_FILE_PATH.equals(key)) {
                    JSONObject tempFileMap = jsonObject.getJSONObject(FormUtil.PROPERTY_TEMP_FILE_PATH);
                    JSONArray tempFiles = tempFileMap.names();
                    if (tempFiles != null && tempFiles.length() > 0) {
                        for (int l = 0; l < tempFiles.length(); l++) {                        
                            List<String> rpValues = new ArrayList<String>();
                            String rpKey = tempFiles.getString(l);
                            JSONArray tempValues = tempFileMap.getJSONArray(rpKey);
                            if (tempValues != null && tempValues.length() > 0) {
                                for (int m = 0; m < tempValues.length(); m++) {
                                    String path = tempValues.getString(m);
                                    File file = FileManager.getFileByPath(path);
                                    if (file != null & file.exists()) {
                                        String newPath = UuidGenerator.getInstance().getUuid() + File.separator + file.getName();
                                        FileUtils.copyFile(file, new File(FileManager.getBaseDirectory(), newPath));
                                        rpValues.add(newPath);
                                    }
                                }
                            }
                            row.putTempFilePath(rpKey, rpValues.toArray(new String[]{}));
                        }
                    }
                } else {
                    String value = jsonObject.getString(key);
                    row.setProperty(key, value);
                }
            }
            row.setId(recordId);
            formData.setStoreBinderData(form.getStoreBinder(), rows);
            return formData;
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, processId);
            return null;
        }
    }
    
    protected void clearFiles(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            if (jsonObject.has(FormUtil.PROPERTY_TEMP_FILE_PATH)) {
                JSONObject tempFileMap = jsonObject.getJSONObject(FormUtil.PROPERTY_TEMP_FILE_PATH);
                JSONArray tempFiles = tempFileMap.names();
                if (tempFiles != null && tempFiles.length() > 0) {
                    for (int l = 0; l < tempFiles.length(); l++) {                        
                        List<String> rpValues = new ArrayList<String>();
                        String rpKey = tempFiles.getString(l);
                        JSONArray tempValues = tempFileMap.getJSONArray(rpKey);
                        if (tempValues != null && tempValues.length() > 0) {
                            for (int m = 0; m < tempValues.length(); m++) {
                                String path = tempValues.getString(m);
                                FileManager.deleteFileByPath(path);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
        }
    }
    
    public static Form getForm(String formDefId) {
        Form form = null;
        if (formDefId != null && !formDefId.isEmpty()) {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            if (appDef != null) {
                FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
                FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
                FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);

                if (formDef != null) {
                    String json = formDef.getJson();
                    form = (Form) formService.createElementFromJson(json);
                }
            }
        }
        return form;
    }
}
