/**
 * 
 */
package bii.nrrd;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;

/**
 * This class contains the key information that describes a nrrd file
 * The expectation is that it will be generated by parsing an existing file
 * before loading.  It will also be generated before saving a file.
 * It should be handed off to NrrdFile reader/writer in order to save/load
 * It is conceptually similar to ImageJ's FileInfo class, but I have decided
 * not to derive it from that because I would like to make this package
 * detachable from ImageJ.
 * @author jefferis
 *
 */
public class NrrdInfo {
	/**
	 * @author jefferis
	 *
	 */
	
	// this will contain the header info that we parse
	NrrdHeader nh;

	public static final int NRRD_DIM_MAX = 16;
	public static final int NRRD_SPACE_DIM_MAX = 8;
	public static final int NRRD_UNKNOWN=-1;
	public static final int NRRD_FALSE=0;
	public static final int NRRD_TRUE=1;
	public static final int NRRD_BIG_ENDIAN=4321;
	public static final int NRRD_LITTLE_ENDIAN=1234;

	// GJ: decided it was simpler just to stay as text
	String type, encoding;
	int dim=NRRD_UNKNOWN;
	long[] sizes;
	long nsamples, nbytes; // the total data size in samples and bytes 
	
	int endian=NRRD_UNKNOWN;
	int lineSkip=0;
	long byteSkip=0;

	// the nrrd file or the nhdr file (including suffix)
	boolean detachedHeader;
	// this will point either to a combined nrrd file or the header file
	public String primaryFileDirectory=null;
	public String primaryFileName=null;
	public File[] dataFiles=null;
	
	Object data;
	NrrdAxisInfo[] nai;
	
	String content,sampleUnits;
	String contentType; // gj - my own
	String space;
	int spaceDim;
	String[] spaceUnits;
	double[] spaceOrigin;
	double[][] measurementFrame;
	double oldMin,oldMax;

	// Per axis info - there will be dim of these
	private double[] spacings;
	private double[] thicknesses;
	private double[] axismins;
	private double[] axismaxs;
	private String[] centers;
	private String[] labels;
	private String[] kinds;
	private String[] units;

	private int dataFileSubDim=NRRD_UNKNOWN;	
	private long dataFileByteSize;
	
	public static final String[] int8Types={"char", "int8","int8_t", "signed char"};
	public static final String[] uint8Types={"uchar", "uint8","uint8_t", "unsigned char"};
	public static final String[] int16Types={"int16", "int16_t","short", "short int", "signed short", "signed short int"};
	public static final String[] uint16Types={"uint16", "uint16_t","unsigned short", "unsigned short int","ushort"};
	public static final String[] int32Types={"int", "int32", "int32_t", "signed int"};
	public static final String[] uint32Types={"uint", "uint32", "uint32_t", "unsigned int"};
	public static final String[] int64Types={"int64","int64_t","long long", "long long int", "longlong", "signed long long", "signed long long int"};
	public static final String[] uint64Types={"uint64", "uint64_t","ulonglong", "unsigned long long", "unsigned long long int"};
	
//	public static final int NRRD_TYPE_DEFAULT=0;
//	public static final int NRRD_TYPE_CHAR=1;         
//	public static final int NRRD_TYPE_UCHAR=2;         
//	public static final int NRRD_TYPE_SHORT=3;     
//	public static final int NRRD_TYPE_USHORT=4;        
//	public static final int NRRD_TYPE_INT=5;       
//	public static final int NRRD_TYPE_UINT=6;         
//	public static final int NRRD_TYPE_LLONG=7;        
//	public static final int NRRD_TYPE_ULLONG=8;       
//	public static final int NRRD_TYPE_FLOAT=9;      
//	public static final int NRRD_TYPE_DOUBLE=10;       
//	public static final int NRRD_TYPE_BLOCK=11;     
//	public static final int NRRD_TYPE_LAST=12;
		
	/**
	 * Constructor takes a NrrdHeader as argument
	 * @param nh
	 */
	public NrrdInfo(NrrdHeader nh){
		this.nh=nh;
	}
	
	public void parseHeader() throws Exception {
		try{	
			// Temp variables to store field values
			String[] sa; int[] ia; long[] la; double[] da;

			// BASIC information
			encoding= getStandardEncoding(getStringFieldChecked("encoding",1, true)[0]);
			if(encoding == null) throw new Exception("Unknown encoding: "+getStringField("type"));
	
			type= getStandardType(getStringFieldChecked("type",1, true)[0]);
			if(type == null) throw new Exception("Unknown data type: "+getStringField("type"));
	
			dim=getIntegerFieldChecked("dimension", 1, true)[0];
			if(dim<1 || dim>NRRD_DIM_MAX) throw new Exception("dim out of range:"+dim);
			
			sizes=getLongFieldChecked("sizes", dim, true);		
			
			int byteSize;
			if(type.equals("block")){
				byteSize=getIntegerFieldChecked("block size", 1, true)[0];
			} else {
				byteSize=getByteSize(type);
			}
			if (byteSize<1) throw new Exception(
					"Inferred byte size less than 1; check type or block size specification");
			nsamples=sizes[0];
			for(int i=1;i<dim;i++) nsamples*=sizes[i];
			if(nsamples<1) throw new Exception(
					"Invalid number of samples: "+nsamples+"; check sizes field");
			nbytes=nsamples*(long)byteSize;
			
			// other per file information
//			endian, line skip, byte skip, data file, content, number (ignored)
//			min, max, old min, old max, sample units
			if(!type.equals("block") && !encoding.equals("txt") && byteSize>1) {
				String endianStr=getStringFieldChecked("endian", 1, true)[0];
				if(endianStr.equals("little")) endian=NRRD_LITTLE_ENDIAN;
				else if(endianStr.equals("big")) endian=NRRD_BIG_ENDIAN;
				else throw new Exception("Unknown endian specification: "+endianStr);
			}
			
			ia=getIntegerFieldChecked("line skip", 1, false);
			if(ia!=null) lineSkip=ia[0];
			
			la=getLongFieldChecked("byte skip", 1, false);
			if(la!=null) byteSkip=la[0];
			
			primaryFileDirectory=nh.directory;			
			primaryFileName=nh.filename;
			if(nh.detachedHeader){
				String[] df=getStringField("data file");
				if(df==null) throw new Exception("Supposed to be a detached header file, but no data file field");
				if(df.length==1 || df.length==2){
					if(df[0].equals("LIST")){
						// should be a list type
						if(nh.dataFiles==null || nh.dataFiles.size()==0) throw new Exception
							("No data files listed after data file LIST line");
						if(df.length==2) dataFileSubDim=new Integer(df[1]).intValue();
						else dataFileSubDim=dim-1;
						dataFiles=new File[nh.dataFiles.size()];
						
						for(int i=0;i<dataFiles.length;i++){
							dataFiles[i]=makeCheckedFile((String) nh.dataFiles.get(i));
						}
					} else {
						dataFiles=new File[1];
						dataFiles[0]=makeCheckedFile(df[0]);
					}
				} else {
					// Should be a format specifier type
					String dataFNFormat=df[0];
					int dataFNMin, dataFNMax,dataFNStep;
					
					try{
						dataFNMin=new Integer(df[1]).intValue();
						dataFNMax=new Integer(df[2]).intValue();
						dataFNStep=new Integer(df[3]).intValue();
						if(df.length==5) dataFileSubDim=new Integer(df[4]).intValue();
						else dataFileSubDim=dim-1;
					} catch (NumberFormatException e){
						throw new Exception("Could not parse data file field; expected data file: <format> <min> <max> <step> [<subdim>]");
					}
					
					int numFiles=1+(dataFNMax-dataFNMin)/dataFNStep;
					int num=dataFNMin;
					dataFiles=new File[numFiles];
					try{
						;
						for	(int i=0;i<numFiles;i++){
							Formatter f = new Formatter().format(dataFNFormat, num);
							//System.out.println("looking for file: "+f.toString());
							dataFiles[i]=makeCheckedFile(f.toString());
							num+=dataFNStep;
						}
					} catch(IOException e) {
						throw new IOException("Unable to find data files referred to by data file field");
					} catch (Exception e) {
						throw new Exception("Unable to process format specifier data file fields with Java<1.5");
					}
				}
				
				// OK now validate number of provided files				
				if(dataFileSubDim!=NRRD_UNKNOWN){
					if(dataFileSubDim<1 || dataFileSubDim>dim) throw new 
					Exception("Detached header subdim specification must be in range [1,"+dim+"]");
					if(dataFileSubDim==dim){
						// Check that the number of 'slabs' divides into number of samples
						if((dataFiles.length%nsamples)!=0) throw new Exception
							("Number of slabs indicated by \"data file\" ("+dataFiles.length+
									") does not divide evenly into number of samples ("+nsamples+")");
							
						dataFileByteSize=nbytes/dataFiles.length;
					} else if(dataFileSubDim < (dim-1)){
						// When <subdim> is less than D-1 
						// (for example, giving a 4-D volume one 2-D slice at a time), 
						// the number of data files can be determined by the product 
						// of one or more of the slowest axes
						
						// e.g. 3 512 512 88 20 (C X Y Z T)
						// if subdim == 3 then => 88 x 20 slabs of 3 x 512 x 512
						// dim=5 
						// i=dim-1=4
						
						int i=dim-1;
						int nFiles=(int) sizes[i];
						while(i>=dataFileSubDim) nFiles*=sizes[i];
						if(nFiles!=dataFiles.length) throw new Exception
							("Number of data files indicated by \"data file\" ("+dataFiles.length+
								") does not match product of dimension sizes >"+dataFileSubDim+" (ie "+nFiles+")");
					} else {
						// default: dataFileSubdim==dim-1
						if(dataFiles.length>1 && (dataFiles.length!=sizes[dim-1])) throw new Exception
						("Number of data files indicated by \"data file\" ("+dataFiles.length+
								") does not match final dimension size "+sizes[dim-1]);
					}
				}
			}
			
			
			// SPACE info
			// space, space dimension, space units, space origin, space directions, measurement frame
			sa=getStringFieldChecked("space",1,false);
			if(sa!=null) processSpace(sa[0]);
			if(spaceDim<1){
				// we couldn't find a space field so should be explicit space info
				ia=getIntegerFieldChecked("space dimension",1,false);
				if(ia!=null) spaceDim=ia[0];
			}
			String[] sd=null;
			if(spaceDim>0){
				// Space directions must be provided if we have a space
				sd=getStringFieldChecked("space directions",dim,true);
				// other space fields optional
				spaceUnits=getStringFieldChecked("space units",spaceDim,false);
				sa=getStringFieldChecked("space origin",1,false);
				if(sa!=null) spaceOrigin=getVector(sa[0],spaceDim);
				
				String[] mf=getStringFieldChecked("measurement frame",dim,false);
				// Process the measurement frame if required
				processMeasurementFrame(mf);
			}
			
			// FETCH general PER AXIS info (there should be dim of these fields
			// spacings, thicknesses, axis mins, axis maxs, centers, labels, units, kinds
			spacings=getDoubleFieldChecked("spacings",dim,false);
			thicknesses=getDoubleFieldChecked("thicknesses",dim,false);
			axismins=getDoubleFieldChecked("axis mins",dim,false);
			axismaxs=getDoubleFieldChecked("axis maxs",dim,false);
			centers=getStringFieldChecked("centers",dim,false);
			labels=getStringFieldChecked("labels",dim,false);
			units=getStringFieldChecked("units",dim,false);
			kinds=getStringFieldChecked("kinds",dim,false);
			
			// SET per axis info
			nai=new NrrdAxisInfo[dim];
			for(int i = 0; i<dim;i++){
				nai[i]=new NrrdAxisInfo();
				nai[i].size=sizes[i];
				if(sd!=null) nai[i].setSpaceDirection(getVector(sd[i], spaceDim));
				if(axismins!=null) nai[i].setMin(axismins[i]);
				if(axismaxs!=null) nai[i].setMax(axismaxs[i]);
				if(units!=null) nai[i].setUnits(units[i]);

				if(centers!=null) nai[i].center=centers[i];
				if(labels!=null) nai[i].label=labels[i];
				if(kinds!=null) nai[i].kind=kinds[i];
			}

		} catch (Exception e){
			throw new Exception ("nrrd: trouble parsing header for field: "+e);
		}
	}
	
	File makeCheckedFile(String path) throws IOException {
		File f= new File(path);
		if(f.getParent()==null) f=new File(primaryFileDirectory,path);
		if (!f.exists()) throw new IOException("Unable to find file: "+path);
		return f;
	}
	
	boolean processMeasurementFrame(String[] mf) throws Exception {
		if (mf == null) return false; 
		measurementFrame = new double[spaceDim][spaceDim];
		try {
			for(int i=0;i<spaceDim;i++){
				// NB measurementFrame[i] refers to the ith column, see:
				// http://teem.sourceforge.net/nrrd/format.html#measurementframe
				measurementFrame[i]=getVector(mf[i],spaceDim);
			}
		} catch (Exception e) {
			measurementFrame=null;
			throw new Exception("trouble parsing measurement frame:"+ e);
		}
		return true;
	}
	
	double[] getVector(String vecStr, int vecLen) throws Exception {
		// (a,b,c)
		double[] da;
		// should we trim?  should have been done before!
		if(vecStr.equals("none")) return null;
		if(vecStr.startsWith("(") && vecStr.endsWith(")")){
			
			String[] sa=vecStr.substring(1, vecStr.length()-1).split(",");
			if(vecLen!=sa.length) throw new Exception("Vector "+vecStr+" should have length: "+vecLen);
			da=new double[vecLen];
			for(int i=0;i<vecLen;i++){
				try{ 
					if(sa[i].equals("nan")) da[i]=Double.NaN;
					else da[i]=new Double(sa[i]).doubleValue(); 
				} catch (NumberFormatException e){
					throw new Exception("Can't parse component: "+sa[i]+" of vector: "+vecStr);
				}
			}
		} else throw new Exception("String "+vecStr+" does not look like vector.");
		return da;
	}
	
	int getByteSize(String stype){
		if(stype.endsWith("int8")) return 1;
		if(stype.endsWith("int16")) return 2;
		if(stype.endsWith("int32") || stype.equals("float")) return 4;
		if(stype.endsWith("int64") || stype.equals("double")) return 8;
		return -1;
	}
	
	public String getStandardType(String stype) {
		if(stype.equals("float") || stype.equals("double") || stype.equals("block")) return stype;		
		if(Arrays.binarySearch(int8Types, stype)>=0) return "int8";
		if(Arrays.binarySearch(uint8Types, stype)>=0) return "uint8";
		if(Arrays.binarySearch(int16Types, stype)>=0) return "int16";
		if(Arrays.binarySearch(uint16Types, stype)>=0) return "uint16";
		if(Arrays.binarySearch(int32Types, stype)>=0) return "int32";
		if(Arrays.binarySearch(uint32Types, stype)>=0) return "uint32";
		if(Arrays.binarySearch(int64Types, stype)>=0) return "int64";
		if(Arrays.binarySearch(uint64Types, stype)>=0) return "uint64";
		return null;
	}
	String getStandardEncoding(String senc) {
		if(senc.equals("raw") || senc.equals("hex")) return senc;
		if(senc.equals("txt") || senc.equals("text") || senc.equals("ascii")) return "txt";
		if(senc.equals("gz") || senc.equals("gzip")) return "gz";
		if(senc.equals("bz2") || senc.equals("bzip2")) return "bz2";
		return null;
	}
	boolean processSpace(String s) throws Exception {
		if(s==null) return false;
		if(s.equals("ras") || s.equals("right-anterior-superior")){
			space="right-anterior-superior";
			spaceDim=3;
		} else if (s.equals("rast") || s.equals("right-anterior-superior-time")){
			space="right-anterior-superior-time";
			spaceDim=4;
			
		} else if(s.equals("las") || s.equals("left-anterior-superior")){
			space="left-anterior-superior";
			spaceDim=3;
		} else if (s.equals("last") || s.equals("left-anterior-superior-time")){
			space="left-anterior-superior-time";
			spaceDim=4;
		} else if(s.equals("lps") || s.equals("left-posterior-superior")){
			space="left-posterior-superior";
			spaceDim=3;
		} else if (s.equals("lpst") || s.equals("left-posterior-superior-time")){
			space="left-posterior-superior-time";
			spaceDim=4;
		} else if( s.startsWith("scanner-xyz") || s.startsWith("3d-right-handed") ||
				s.startsWith("3d-left-handed") ){
			space=s;
			if(s.endsWith("-time")) spaceDim=4;
			else spaceDim=3;
		} else {
			throw new Exception ("Unknown space: "+s);
		}
		return true;
	}
	
	public String[] getStringField(String key){
		if(nh.fields.containsKey(key)){
			return (String[]) nh.fields.get(key);
		} else return null;
	}
		 
	public double[] getDoubleField(String key){
		if(nh.fields.containsKey(key)){
			String[] sa=(String[]) nh.fields.get(key);
			double[] da=new double[sa.length];
			for(int i=0;i<sa.length;i++){
				// only string "NaN" -> (double) NaN
				if(sa[i].equals("nan")) da[i]=Double.NaN;
				else da[i] = new Double(sa[i]).doubleValue();
			}
			return da;
		} else return null;
	}

	public long[] getLongField(String key){
		if(nh.fields.containsKey(key)){
			String[] sa=(String[]) nh.fields.get(key);
			long[] la=new long[sa.length];
			for(int i=0;i<sa.length;i++){
				la[i] = new Long(sa[i]).longValue();
			}
			return la;
		} else return null;
	}
	public int[] getIntegerField(String key){
		if(nh.fields.containsKey(key)){
			String[] sa=(String[]) nh.fields.get(key);
			int[] ia=new int[sa.length];
			for(int i=0;i<sa.length;i++){
				ia[i] = new Integer(sa[i]).intValue();
			}
			return ia;
		} else return null;
	}

	public String[] getStringFieldChecked(String key, int n, boolean required) throws Exception {
		String[] sa;
		sa=getStringField(key);
		if(sa==null){
			if(required) throw new Exception("Required field: "+key+" is missing");
			else return null;
		}
		if(sa.length!=n) throw new Exception("Field: "+key+" must have exactly "+n+" values");
		return sa;
	}

	public int[] getIntegerFieldChecked(String key, int n, boolean required) throws Exception {
		int[] ia;
		ia=getIntegerField(key);
		if(ia==null){
			if(required) throw new Exception("Required field: "+key+" is missing");
			else return null;
		}
		if(ia.length!=n) throw new Exception("Field: "+key+" must have exactly "+n+" values");
		return ia;
	}

	public long[] getLongFieldChecked(String key, int n, boolean required) throws Exception {
		long[] la;
		la=getLongField(key);
		if(la==null){
			if(required) throw new Exception("Required field: "+key+" is missing");
			else return null;
		}
		if(la!=null && la.length!=n) throw new Exception("Field: "+key+" must have exactly "+n+" values");
		return la;
	}
	public double[] getDoubleFieldChecked(String key, int n, boolean required) throws Exception {
		double[] da;
		da=getDoubleField(key);
		if(da==null){
			if(required) throw new Exception("Required field: "+key+" is missing");
			else return null;
		}
		if(da!=null && da.length!=n) throw new Exception("Field: "+key+" must have exactly "+n+" values");
		return da;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		System.out.println("Double nan "+new Double("NaN").doubleValue());
	}

}

class NrrdAxisInfo {
	long size;
	double spacing=Double.NaN;
	double min=Double.NaN, max=Double.NaN;
	double[] spaceDirection=null;
	String center;
	String kind;
	String label,units;
	
	public double getMax() {return max;}
	public double getMin() {return min;}

	public String getUnits() {return units;}
	
	public double getSpacing() {return spacing;}
	
	public double[] getSpaceDirection(){ return spaceDirection;}

	public void setUnits(String units) throws Exception {
		if(spaceDirection==null) this.units=units;
		else throw new Exception ("Conflict between existing space direction and per axis unit field");
	}
	
	public void setMin (double min) throws Exception {
		if(spaceDirection==null) this.min=min;
		else throw new Exception ("Conflict between existing space direction and axis min field");
	}
	
	public void setMax (double max) throws Exception {
		if(spaceDirection==null) this.max=max;
		else throw new Exception ("Conflict between existing space direction and axis max field");
	}
	
	public void setSpacing (double spacing) throws Exception {
		if(spaceDirection==null) this.spacing=spacing;
		else throw new Exception ("Conflict between existing space direction and spacing field");
	}
	
	public void setSpaceDirection (double[] spaceDirection) throws Exception {
		if(!Double.isNaN(spacing)) throw new 
			Exception("Conflict between existing spacing field and space direction");

		if(!Double.isNaN(max)) throw new 
			Exception("Conflict between existing axis max field and space direction");

		if(!Double.isNaN(min)) throw new 
			Exception("Conflict between existing axis min field and space direction");

		if(units!=null && !units.equals("")) throw new 
			Exception("Conflict between existing non-empty units field and space direction"); 

		this.spaceDirection=spaceDirection;
	}
}