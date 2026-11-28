package dev.anjo7778.sao.simulation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.anjo7778.sao.simulation.CityArchitecture.Style;

/** Immutable fixed building layers. No random geometry, furniture placement, or per-lot construction rules. */
public final class BuildingTemplates {
    public static final int MAX_HEIGHT=28;
    public static final String SYMBOLS=".=#wpxzrgk+<>v^lBCFATbhSiLJ";
    public record Blueprint(Style style,int width,int height,int depth,char[][][] layers){
        public int halfWidth(){return width/2;}
        public int halfDepth(){return depth/2;}
        public boolean covers(int x,int z){return Math.abs(x)<=halfWidth()&&Math.abs(z)<=halfDepth();}
        public char at(int x,int y,int z){
            if(y<0||y>=height||!covers(x,z))return '.';
            return layers[y][z+halfDepth()][x+halfWidth()];
        }
    }
    private static final Map<Style,Blueprint> MODELS=load();
    private BuildingTemplates(){}
    public static Blueprint forStyle(Style style){return MODELS.get(style);}
    public static Blueprint forPlot(CityPlan.Plot p){return forStyle(CityArchitecture.style(p));}
    private static Map<Style,Blueprint> load(){
        EnumMap<Style,Blueprint> out=new EnumMap<>(Style.class);
        for(Style style:Style.values()){
            String path="/data/sao/templates/v1/"+style.name().toLowerCase(Locale.ROOT)+".txt";
            try(InputStream stream=BuildingTemplates.class.getResourceAsStream(path)){
                if(stream==null)throw new IOException("Missing "+path);
                try(BufferedReader in=new BufferedReader(new InputStreamReader(stream,StandardCharsets.US_ASCII))){
                    String[] sizes=in.readLine().trim().split(" ");
                    if(sizes.length!=3)throw new IOException("Invalid header");
                    int w=Integer.parseInt(sizes[0]),h=Integer.parseInt(sizes[1]),d=Integer.parseInt(sizes[2]);
                    if(w<9||w>23||w%2==0||d<9||d>23||d%2==0||h<5||h>MAX_HEIGHT+1)throw new IOException("Invalid dimensions");
                    char[][][] layers=new char[h][d][w];
                    for(int y=0;y<h;y++)for(int z=0;z<d;z++){
                        String row=in.readLine();
                        if(row==null||row.length()!=w||row.chars().anyMatch(c->SYMBOLS.indexOf(c)<0))throw new IOException("Invalid layer "+y+"/"+z+" in "+path);
                        layers[y][z]=row.toCharArray();
                    }
                    if(in.readLine()!=null)throw new IOException("Trailing data");
                    out.put(style,new Blueprint(style,w,h,d,layers));
                }
            }catch(IOException|RuntimeException ex){throw new IllegalStateException("Cannot load template "+path,ex);}
        }
        return Collections.unmodifiableMap(out);
    }
}
