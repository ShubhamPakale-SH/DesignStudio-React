// Centralized API endpoint paths (relative to feature base URLs)

// Folder (Form Design Group) endpoints
export const FORM_DESIGN_GROUP_LIST =
  "FormDesignGroup/FormDesignGroupList?tenantId=1&_search=false&nd=1756967066466&rows=10000&page=1&sidx=FormGroupId&sord=asc";
export const FORM_GROUP_MAPPING_LIST_BASE =
  "FormDesignGroup/FormGroupMappingList?tenantId=1&formGroupId=";

// Design Compile
export const GET_DOCUMENT_DESIGN_LIST =
  "FormDesignCompiler/DocumentDesignList?tenantId=1&_search=false&nd=1756984255934&rows=10000&page=1&sidx=DocumentDesignName&sord=asc";

export const COMPILE_DESIGNS_ENDPOINT =
  "FormDesignCompiler/CompileDocumentDesignThroughHangfire";

// Design (Document Design Types)
export const Document_Design_List = "FormDesign/DocumentDesignType";

// Design (Documents by type and versions) - base endpoints; append required ids
export const Form_DesignList_ByDocType =
  "FormDesign/FormDesignListByDocType?tenantId=1&documentDesignTypeId=";
export const FormDesign_VersionList =
  "FormDesign/FormDesignVersionList?tenantId=1&formId=";
