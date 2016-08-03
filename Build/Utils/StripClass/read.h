ClassFile *read_ClassFile(FILE *fyle);
cp_info **read_constant_pool(FILE *fyle, int count);
cp_info *read_cp_info(FILE *fyle);
field_info **read_fields(FILE *fyle, ClassFile *cf, int count);
field_info *read_field_info(FILE *fyle, ClassFile *cf);
method_info **read_methods(FILE *fyle, ClassFile *cf, int count);
method_info *read_method_info(FILE *fyle, ClassFile *cf);
attribute_info **read_attributes(FILE *fyle, ClassFile *cf, int count);
attribute_info *read_attribute_info(FILE *fyle, ClassFile *cf);
u1  read_u1(FILE *fyle);
u1 *read_u1_array(FILE *fyle, int length);
u2  read_u2(FILE *fyle);
u2 *read_u2_array(FILE *fyle, int length);
u4  read_u4(FILE *fyle);
attribute_info **scan_attributes(u1 **scanptr, ClassFile *cf, int count);
attribute_info *scan_attribute_info(u1 **scanptr, ClassFile *cf);
annotation *scan_annotation(u1 **scanptr);
annotation **scan_annotations(u1 **scanptr, int count);
element_value *scan_element_value(u1 **scanptr);
void scanBytes(u1 **scanptr, u1 *result, int length);
u1  scan_u1(u1 **scanptr);
u1 *scan_u1_array(u1 **scanptr, int length);
u2  scan_u2(u1 **scanptr);
u2 *scan_u2_array(u1 **scanptr, int length);
u4  scan_u4(u1 **scanptr);
attribute_value *decode_attribute_value(ClassFile *cf, attribute_info *att);
