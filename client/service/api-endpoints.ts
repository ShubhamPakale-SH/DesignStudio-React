// Centralized API endpoint paths (relative to feature base URLs)

// Folder (Form Design Group) endpoints
export const FORM_DESIGN_GROUP_LIST =
  "FormDesignGroup/FormDesignGroupList?tenantId=1&_search=false&nd=1756967066466&rows=10000&page=1&sidx=FormGroupId&sord=asc";

// Design Compile
export const GET_DOCUMENT_DESIGN_LIST =
  "FormDesignCompiler/DocumentDesignList?tenantId=1&_search=false&nd=1756984255934&rows=10000&page=1&sidx=DocumentDesignName&sord=asc";

export const COMPILE_DESIGNS_ENDPOINT =
  "FormDesignCompiler/CompileDocumentDesignThroughHangfire";

// Design (Document Design Types)
export const Document_Design_List =
  "FormDesign/DocumentDesignType";
