package org.pente.gameServer.server;

import java.io.*;
import java.util.*;

//import org.apache.log4j.*;

//import org.pente.game.*;

/** Mark Mammel's Pente and Keryo-Pente AI
 *  This code was converted from c and therefore
 *  has some weirdness about it.  I had to convert
 *  some pointer arithmetic and so forth.
 *  @author Mark Mammel, Peter Hewitt
 */
public class MarksAIPlayer extends AbstractAIPlayer {

    //private static Category log4j = Category.getInstance(
    //    MarksAIPlayer.class.getName());
        
	private int game;
	private int level;
	private int seat;
	private int moveNum;
    private List moves;
	private int size = 19;
	
	private final int bsize = 912;
	private final int tsize = 943;
    
    private final int openingBookSize = 600;
	
	private int cp, xf, tn, lvl, npl, cflag, vct;

	private int cap1, ppx, ppy, ppd, px[]=new int[24], py[]=new int[24], pd[]=new int[24], pf[]=new int[24];
	private int xfc, yfc, obfl, cob, crot, obsize, extnt, npm, pmn;
	private int clr[]=new int[7], p[]=new int[7], cc[][]=new int[18][7], sx[]=new int[362], sy[]=new int[362];

	private int dx[] = {-1,0,1,-1,1,0,-1,1};
	private int dy[] = {-1,-1,-1,0,1,1,1,0};
	private int rotx[] = {1,1,1,1,-1,-1,-1,-1};
	private int roty[] = {1,1,-1,-1,-1,-1,1,1};
	private int rotf[] = {0,1,0,1,0,1,0,1};
	private int sco[]=new int[7], mx, my, fr, en, gf, mxx, thrfl, cap5, fefv, ferr;
	private int xoff, yoff, rlct, mvct; 
	private int rlst[]=new int[800], rrot[]=new int[800], nh[]=new int[18], oscr[]=new int[openingBookSize], nom[]=new int[openingBookSize];
	private int bd[][][]=new int[18][size][size], ciel[][]=new int[7][18]; 
	  // 18 levels of 19x19 board
	  // each ply of the search is a level
	  // 0=empty, 1=player 1 stone, 2=player 2;
	  // -1=empty space within 2 spaces of a stone
	  // the computer will only consider moving to '-1'.
	private int plv, bmove, lscr, bscr, cpdr;
	private int fuk[][]=new int[361][8], fhole[][]=new int[750][4], fhn;
	
	/* pointers? int *scores, *table, *obk; */
	private int scores[], table[], obk[];

    private String configDirectory;
	
    public static void main(String args[]) {
    	MarksAIPlayer p = new MarksAIPlayer();
    	p.configDirectory = "/dsg_src/conf/marksAI";
    	p.game = 1;
    	p.init();
    }
    
	public MarksAIPlayer() {
		obfl = 1;
        
        moves = new ArrayList();
	}
	public void setSize(int size) {
		//System.out.println("setSize" + this.size + "," + size);
		if (this.size != size) {
			for (int i = 0 ; i < openingBookSize; i++) {
				for (int j = 0; j < 24; j++) {
					if (obk[i*24+j] != 0) {
						int m = obk[i*24+j];
						//int oldm = m;
					    obk[i*24+j]=convert(this.size, size, m);
						//System.out.println("convert " + oldm + " to " + m);
					}
				}
			}
			for (int i = 0; i < om2.length; i++) {
				om2[i] = convert(this.size, size, om2[i]);
				//System.out.print(om2[i]+",");
			}
			//System.out.println();

			for (int i = 0; i < om3.length; i++) {
				om3[i] = convert(this.size, size, om3[i]);
				//System.out.print(om3[i]+",");
			}
			//System.out.println();
		}
		
		this.size = size;
		
	}
	public int convert(int oldSize, int newSize, int m) {						int oldm = m;
		int x = m / oldSize;
		int y = m % oldSize;
		x = newSize/2 + (oldSize/2-x);
	    y = newSize/2 + (oldSize/2-y);//or whatever
	    m = y*newSize+x;
	    return m;
	}

    public void setSeat(int seat) {
        this.seat = seat;
        
        if (seat == 1) {
            p[1] = level;
            p[2] = 0;
        }
        else if (seat == 2) {
            p[1] = 0;
            p[2] = level;
        }
    }

    public void setLevel(int level) {
        this.level = level;
        
        if (seat == 1) {
            p[1] = level;
            p[2] = 0;
        }
        else if (seat == 2) {
            p[1] = 0;
            p[2] = level;
        }
    }
    public int getLevel() {
    	return level;
    }

    public void setGame(int game) {
        //log4j.debug("setGame=" + game);
        if (game == 1) {
            this.game = 1;
            //log4j.debug("pente");
        }
        else if (game == 3) {
            this.game = 2;
            //log4j.debug("keryo-pente");
        }
    }
    
    public void setOption(String name, String value) {
        if (name.equals("configDirectory")) {
            configDirectory = value;
        }
    }
        
    public void addMove(int move) {

        moves.add(new Integer(move));
        
        sx[moveNum + 1] = move % size;
        sy[moveNum + 1] = move / size;

        tn = ++moveNum;
        dmov();
    }

    /** No easy way to undo moves so just clear everything
     *  and start over adding all but the last move.
     */
    public void undoMove() {

        moves.remove(moves.size() - 1);
        List oldMoves = new ArrayList(moves);
        
        clear();
        
        for (Iterator it = oldMoves.iterator(); it.hasNext();) {
            addMove(((Integer) it.next()).intValue());
        }
    }

    public int getMove() throws InterruptedException {

        startThinking();

        tn = moveNum + 1;
        int move = cmove();
        
        stopThinking();
        
        return move;
    }	

	public void init() {
        
        clear();
        
        try {
            loadLocalFiles();
        } catch (Throwable t) {
            //log4j.error("Error initializing.", t);
        	t.printStackTrace();
        }
    }
	public void init(InputStream scs, InputStream opnbk, InputStream tbl) throws Throwable {
		clear();

        initDataStructures();
        
		loadPenteTBL(tbl);
        loadPenteSCS(scs);
        loadOPNGBK(opnbk);
	}
    
    public void destroy() {   
    }



	private void loadLocalFiles() throws Throwable {

        initDataStructures();

        if (configDirectory == null) {
            configDirectory = "";
        }

        FileInputStream in = new FileInputStream(configDirectory + "/pente.tbl");
        loadPenteTBL(in);
        in.close();
        
        in = new FileInputStream(configDirectory + "/pente.scs");
        loadPenteSCS(in);
        in.close();

        if (game == 1) {
            in = new FileInputStream(configDirectory + "/opngbk.pen");
            loadOPNGBK(in);
        } else if (game == 2) {
            in = new FileInputStream(configDirectory + "/opngbk.kpn");
            loadOPNGBK(in);
        }
        in.close();

        //log4j.info("done loading files");
        //System.out.println("done loading files");
	}
	
	private void initDataStructures() {

		scores = new int[bsize*14];
		table = new int[tsize*4];
		obk = new int[openingBookSize * 24];
	}
	
	private void loadPenteSCS(InputStream in) throws Exception {
		
		for(int i=0; i<912; i++) {
			for(int j=0; j<14; j++) {

				int sint=getShort(in);
				scores[i*14+j]=sint;
				//System.out.println(scores[i*14+j]);
			}
		}
		
		//log4j.info("loaded scs");
		//System.out.println("loaded scs");
	}
	
	private void loadPenteTBL(InputStream in) throws Exception {
		
		for(int i=0; i<943; i++) {
			for(int j=0; j<4; j++) {

				int sint=getShort(in);
				table[i*4+j]=sint;
				//System.out.println(table[i*4+j]);
			}
		}

		//log4j.info("loaded tbl");
		//System.out.println("loaded tbl");
	}
	
	private void loadOPNGBK(InputStream in) throws Exception {
		
		obsize=getShort(in);
		if(obsize>=openingBookSize) return;
		cob=0;
		int i=0, sint=0, ef=0;
		
		do {
			i=cob;
			sint=ef=getShort(in);
			nom[i]=sint;
			if(ef!=-1) {
				getShort(in);
				sint=getShort(in);
				oscr[i]=sint;
				//System.out.println(sint);
				for(int j=0; j<nom[i]; j++) {
					sint=getShort(in);
					//System.out.println(sint);
					obk[i*24+j]=sint;
				}
				cob++;
			}
		} while(ef!=-1 && cob<obsize);
		

		//System.out.println("loaded opnbk");
		//log4j.info("loaded gbk");
	}

    public int[] getTbl() {
        return table;
    }
    public int[] getSrc() {
        return scores;
    }
	//need some trickery here to get the same thing the c code
	//does.
	private int getShort(InputStream s) throws Exception {
		
		int i1=s.read(); if(i1==-1) return -1;
		int i2=s.read(); if(i2==-1) return -1;
		
		i2 = i2<<8;
		i2 = i2 | i1;  
		i2 = ((short)i2);
		
		return i2;
	}

	public void clear() {

        for (int i = 0; i < sx.length; i++) {
            sx[i] = 0;
            sy[i] = 0;
        }

		for (int x=0; x<size; x++)   //clear board at beginning
		  for (int y=0; y<size; y++)
			bd[0][x][y]=0;
		ciel[1][0]=24;  //set cieling of search to max
		ciel[2][0]=24;
		cc[0][1]=0; //number of captures for p1 (at level 0)
		cc[0][2]=0; // "" p2
		npl=2;   //two players - cannot change in this version!
		vct=0;   //no vct search
		obfl=1;  //opening book on
		extnt=2; //extent 2
		tn=0;
		moveNum=0;
		moves.clear();
	}
	
	public void dmov()
	{
		//System.out.println("start dmov() " + tn);
		
		int i, j, k, x, y, cx, cy, obi, mfl, kfl;
		int c1,c2,c3,c4,c5,c6,c7,c8,d;

		cp=2-tn%2;  //set current player
		bd[0][sx[tn]][sy[tn]]=2-tn%2;  //place piece (1 or 2) on board
		for (x=sx[tn]-extnt; x<sx[tn]+1+extnt; x++) //set spaces around piece to -1
		  for (y=sy[tn]-extnt; y<sy[tn]+1+extnt; y++) //for consideration by ai
		    if (x>=0 && x<size && y>=0 && y<size)
		      if (bd[0][x][y]==0) bd[0][x][y]=-1;

		  // chk captures
		  for (d=0; d<8; d++) {
			c1=sx[tn]+dx[d];
			c2=sy[tn]+dy[d];
			c3=c1+dx[d];
			c4=c2+dy[d];
			c5=c3+dx[d];
			c6=c4+dy[d];
			c7=c5+dx[d];
			c8=c6+dy[d];
			if (c5>=0 && c5<size && c6>=0 && c6<size)
			  if (bd[0][c1][c2]>0 && bd[0][c3][c4]>0 &&
			      bd[0][c1][c2]!=cp && bd[0][c3][c4]!=cp) {
			    if (bd[0][c5][c6]==cp) {
			      cc[0][cp]+=2;
			      bd[0][c1][c2]=-1;
			      bd[0][c3][c4]=-1;
			    }
			    else {
			      if (c7>=0 && c7<size && c8>=0 && c8<size && game==2)
				    if (bd[0][c7][c8]==cp && bd[0][c5][c6]>0) {
				      cc[0][cp]+=3;
				      bd[0][c1][c2]=-1;
				      bd[0][c3][c4]=-1;
				      bd[0][c5][c6]=-1;
				    }
			    }
			  } // if en*2
		  }  // next d

		if (tn==1) {
			xoff=yoff=-size/2;
		}
		if (tn>1 && obfl!=0) {
		//if (tn>1 && obfl) {
		  rlct=0;
		  for (i=0; i<8; i++) {
		    for (obi=0; obi<obsize; obi++) {
		      mfl=1;
		      for (j=1; j<=tn; j++) {
			    cx=(sx[j]+xoff)*rotx[i];
			    cy=(sy[j]+yoff)*roty[i];
				if (rotf[i]!=0) { c1=cx; cx=cy; cy=c1; }
			    //if (rotf[i]) { c1=cx; cx=cy; cy=c1; }
			    kfl=0;
				
				if (obk[obi*24+j-1]==((cy+size/2)*size+cx+size/2)) kfl=1;
			    //if (*(obk+obi*24+j-1)==(cy+9)*size+cx+9) kfl=1;
			    if (j<5) {   // symmetry for moves  1,3 and 2,4
			      k=j+2;
			      if (k>4) k=j-2;
				  if (obk[obi*24+k-1]==((cy+size/2)*size+cx+size/2)) kfl=1;
			      //if (*(obk+obi*24+k-1)==(cy+9)*size+cx+9) kfl=1;
			    }
				if (kfl==0) mfl=0;
			    //if (!kfl) mfl=0;
		      } // next j
		      if (p[3-cp]!=0 && mfl!=0 && (cp==2 && oscr[obi]>5 || cp==1 && oscr[obi]<5))
		      //if (p[3-cp] && mfl && (cp==2 && oscr[obi]>5 || cp==1 && oscr[obi]<5))
			    mfl=0;  //opening book score <5 = p1 adv; >5 = p2 adv
			  if (mfl!=0) {
		      //if (mfl) {
			    rlst[rlct]=obi;
			    rrot[rlct]=i;
			    rlct++;
			    if (rlct>799) rlct=799;
		      }
		    } // next obi
		  } // next i

		  if (rlct==0 && tn==4) {  // offset capture
		  //if (!rlct && tn==4) {  // offset capture
		    xoff=sx[1]-sx[3]-size/2;
		    yoff=sy[1]-sy[3]-size/2;
		    for (i=0; i<8; i++) {
		      for (obi=0; obi<obsize; obi++) {
			    mfl=1;
			    for (j=1; j<=tn; j++) {
			      cx=(sx[j]+xoff)*rotx[i];
			      cy=(sy[j]+yoff)*roty[i];
			      if (rotf[i]!=0) { c1=cx; cx=cy; cy=c1; }
			      //if (rotf[i]) { c1=cx; cx=cy; cy=c1; }
			      kfl=0;
				  if (obk[obi*24+j-1]==((cy+size/2)*size+cx+size/2)) kfl=1;
			      //if (*(obk+obi*24+j-1)==(cy+9)*size+cx+9) kfl=1;
			      if (j<5) {   // symmetry for moves  1,3 and 2,4
			        k=j+2;
			        if (k>4) k=j-2;
					if (obk[obi*24+k-1]==((cy+size/2)*size+cx+size/2)) kfl=1;
			        //if (*(obk+obi*24+k-1)==(cy+9)*size+cx+9) kfl=1;
			      }
				  if (kfl==0) mfl=0;
			      //if (!kfl) mfl=0;
			    } // next j
			    if (p[3-cp]!=0 && mfl!=0 && (cp==2 && oscr[obi]>5 || cp==1 && oscr[obi]<5))
			    //if (p[3-cp] && mfl && (cp==2 && oscr[obi]>5 || cp==1 && oscr[obi]<5))
			      mfl=0;  //opening book score <5 = p1 adv; >5 = p2 adv
			    if (mfl!=0) {
				//if (mfl) {
			      rlst[rlct]=obi;
			      rrot[rlct]=i;
			      rlct++;
			      if (rlct>799) rlct=799;
			    }
		      } // next obi
		    } // next i
		  } // end capture
		  if (rlct==0) {
			  obfl=0;
			  //System.out.println("turn off opening book");
		  }
		  //if (!rlct) obfl=0;
		  else {
			i=(int)(Math.random()*(rlct-1));
			//i=rand()*(rlct-1)/32768;
		    cob=rlst[i];
		    crot=rrot[i];
		  }
		} // if turn>1
		if (tn>4 && tn>=nom[cob]) {
			obfl=0;
			//System.out.println("turn off opening book 2");
		}
		
		//System.out.println("end dmov()");
	}
	
	int om2[] = {181,182,162,163,164,165,144,145};
	int op2[] = {25,36,77,82,93,95,97,99};
	int om3[] = {183,184,202,221,240,260,239,238,237,256,236,235,
		234,252,215,196,177,176,158,139,120,100,
		121,122,123,104,124,125,126,108,145,164};
	
	private int cmove()  // AI routine
        throws InterruptedException
	{
		//System.out.println("start cmov() obfl="+obfl);
		
		int i, x, y, xx,t;

		  //opening moves for turn 2 and 3.

		cp=2-tn%2;
		bmove=0;
		bscr=0;
        
        hlim=4;
        if (game==2) hlim=5;
        
		if (tn==1) bmove=180;
		if (tn==2 && obfl!=0) {
		//if (tn==2 && obfl) {
		  t=(int)(Math.random()*99);
		  //x=rand()*99/32768;
		  i=-1;
		  do i++;
		  while (t>op2[i]);
		  x=om2[i]%size-size/2;
		  y=om2[i]/size-size/2;
		  //System.out.println(i+","+t+","+x+","+y);
		  if (((int)(Math.random()*2)) == 1) x=-x;
		  if (((int)(Math.random()*2)) == 1) y=-y;
		  if (((int)(Math.random()*2)) == 1) { xx=x; x=y; y=xx; }
		  //if (rand()%2) x=-x;
		  //if (rand()%2) y=-y;
		  //if (rand()%2) { xx=x; x=y; y=xx; } 
		  bmove=(y+size/2)*size+x+size/2;
		  //System.out.println(x+","+y+","+bmove);
//		  if (bmove == 84) {
//			  System.out.println("84 " + i);
//		  }
		  i=0;
		}
		else if (tn==2) {
		  do {
			x=7+((int)(Math.random()*3));
			y=7+((int)(Math.random()*3));
			//x=7+rand()/8192; //0-3
		    //y=7+rand()/8192;
		  } while (bd[0][x][y]>0);
		  bmove=y*size+x;
		}
		if (tn==3 && obfl==0) {
		//if (tn==3 && !obfl) {
		  do {
			i=((int)(Math.random()*31));
			//i=rand()*31/32768;
		    x=om3[i]%size;
		    y=om3[i]/size;
		  } while (bd[0][x][y]>0);
		  bmove=y*size+x;
		}
		if (obfl!=0 && tn>2) {
		//if (obfl && tn>2) {
		  if ( ((int)(Math.random()*99)) < tn*6-23 ) {
			  obfl=0;
  			  //System.out.println("turn off opening book 3");
		  }
		  //if (rand()*99/32768 < tn*6-23) obfl=0;
		  else {
			x=obk[cob*24+tn-1]%size-size/2;
		    //x=*(obk+cob*24+tn-1)%19-9;
			y=obk[cob*24+tn-1]/size-size/2;
		    //y=*(obk+cob*24+tn-1)/19-9;
			if (rotf[crot]!=0) { xx=x; x=y; y=xx; }
		    //if (rotf[crot]) { xx=x; x=y; y=xx; }
		    x=x*rotx[crot]-xoff;
		    y=y*roty[crot]-yoff;
		    if (tn==4 && bd[0][x][y]>0) {   // flip moves 2 and 4
			  x=obk[cob*24+tn-3]%size-size/2;
		      //x=*(obk+cob*24+tn-3)%19-9;
			  y=obk[cob*24+tn-3]/size-size/2;
		      //y=*(obk+cob*24+tn-3)/19-9;
			  if (rotf[crot]!=0) { xx=x; x=y; y=xx; }
		      //if (rotf[crot]) { xx=x; x=y; y=xx; }
		      x=x*rotx[crot]-xoff;
		      y=y*roty[crot]-yoff;
		    }
		    bmove=y*size+x;
		  }
		}

		//System.out.println("bmove="+bmove);
		
		if (bmove==0) {
			//System.out.println("no opening book, search");
		//if (!bmove) {
		  plv=p[cp];
		  tree();
		} // end if hmv=0;

		if (bscr<11000) ciel[cp][0]=24;

		lvl=0;
		
		//System.out.println("bmove="+bmove);
		
		//System.out.println("end cmov()");
		
		return bmove;
	}


	public static int getX(int pointer, int array[][]) {

		
		//System.out.println("pointer="+pointer);
		//System.out.println("array[0].length="+array[0].length);
		
		//return pointer%array[0].length;
		return pointer/array[0].length;
	}
	public static int getY(int pointer, int array[][]) {
														   
		//return pointer/array[0].length;
		return pointer%array[0].length;
	}
	//public static int getX(int pointer, int array[][][]) {
	//	return 0;
	//}
	//public static int getY(int pointer, int array[][][]) {
	//	return 0;
	//}
	//public static int getZ(int pointer, int array[][][]) {
	//	return 0;
	//}
	
	private int tree()
        throws InterruptedException
	{
		//System.out.println("start tree()");
		
		int mxor[] = {0,20,18,16,14,12,12,12,12,12,8,8,8,8,8,8,8,8};
		//maximum number of moves to further expand from each level
		int mxvt[] = {0, 1, 3, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15};
		  //depth of VCT for threes
		int mxvf[] = {0, 1, 5, 7, 9,10,11, 12, 13, 14, 15, 16, 17};  //fours
		int minscr=0, ctfl=0, mxlv=0, mxst=0, exfl[]=new int[18], exel[]=new int[18];
		int mv[]=new int[18], mvsco[][][]=new int[18][41][7], mvscr[][]=new int[18][41], mvlst[][]=new int[18][41];
		int scr[][]=new int[18][7], hmv[]=new int[18], mxmv[]=new int[18], tmpindex[]=new int[300];
		// scr[0] is the computers final score after the search
		// hmv is the best move found in the format x+y*19
		int tmpscr[]=new int[300], tmpmv[]=new int[300], tmpsco[][]=new int[300][7];
		
		//converted pointers
		int pmvscr=0, pmv=0, pmxmv=0, pmvlst=0, pmxor=0;
		int pbd0=0, pbd1=0, pexfl=0, pexel=0;
		
		int fl1=0, wfl=0, ii=0, ij=0, x=0, y=0, i=0, j=0, frmo=0;
		int xx=0, yy=0, d=0, c1=0, c2=0, c3=0, c4=0, c5=0, c6=0, c7=0, c8=0, ct=0, sc=0;
		int shi=0, shj=0, shv=0, shw=0, inc=0, loc=0, tyt=0, tyf=0, tys=0;

		hmv[0]=0;
		wfl=0;
		lvl=0;
		fl1=1;
		exfl[0]=3;
		exel[0]=3;
		mxst=1;

		do {
		  do {
            checkStopped();
            
		    lvl++;
		    //pmvscr=mvscr[lvl];
			
			pmvscr=lvl*mvscr[0].length;
			
		    //pmv=&mv[lvl];
			pmv=lvl;
		    //pmxmv=&mxmv[lvl];
			pmxmv=lvl;
		    //pmvlst=mvlst[lvl];
			
			pmvlst=lvl*mvlst[0].length;
			
			//pmxor=&mxor[lvl];
			pmxor=lvl;
		    fr=cp-1+lvl;
		    while (fr>npl) fr-=npl;
		    if (fl1!=0) {
		      ciel[cp][lvl]=ciel[cp][lvl-1];
		      for (i=1; i<=npl; i++)
			    scr[lvl-1][i]= -32000;
		      for (i=0; i<361; i++)    //clear fukumi/legal table
			    for (j=0; j<4; j++) fuk[i][j]=0;
		      fhn=0;
		      ferr=0;
		      //*pmv=-1;
			  mv[pmv]=-1;
		      mvct=1;

		      //for (i=0; i<*pmxor; i++) *(pmvscr+i)=-30000;
			  for(i=0; i<mxor[pmxor]; i++) {
				  mvscr[getX(pmvscr+i, mvscr)][getY(pmvscr+i, mvscr)]=-30000;
			  }

		      minscr=-30000;

		      for (x=0; x<size; x++)
			    for (y=0; y<size; y++)
			      if (bd[lvl-1][x][y]==-1) {
			        sc = eval(x,y);
			        if (-sco[3-fr]>800 && minscr<3000)
			          minscr=3000; //block four
			        en=fr+1;
			        if (en>npl) en=1;
			        tmpscr[mvct]=sc;
			        tmpmv[mvct]=y*size+x;
			        for (ii=1; ii<=npl; ii++) {
			          tmpsco[mvct][ii]=sco[ii];
                    }
			        tmpindex[mvct]=mvct;
			        mvct++;
			        if (mvct>299) mvct=299;
			      } // end if spc
		      for (j=0; j<fhn; j++) { //add in fukumi
			    loc=fhole[j][1];
			    tyt=tyf=0;
			    for (i=0; i<4; i++)
			      if (i!=fhole[j][3]) {
			        tys=fuk[loc][i];
			        if (tys==6) tyt++;
			        if (tys==5) tyt++;
			        if (tys==7 || tys==8) tyf++;
			      }
			    tys=fhole[j][2];
			    if (tys==2 && tyf!=0) tmpscr[fhole[j][0]]+=100;
			    if (tys==3 && tyt!=0) tmpscr[fhole[j][0]]+=100;
			    if (tys==3 && tyf!=0) tmpscr[fhole[j][0]]+=100;
			    if (tys==2 && tyt!=0) tmpscr[fhole[j][0]]+=50;
		      }

		      mvct--;
		      inc=1;  //shell sort
		      do { inc*=3; inc++; }
		      while (inc<=mvct);
		      do {
			    inc/=3;
			    for (shi=inc+1; shi<=mvct; shi++) {
			      shv=tmpscr[shi];
			      shw=tmpindex[shi];
			      shj=shi;
			      while (tmpscr[shj-inc]<shv) {
			        tmpscr[shj]=tmpscr[shj-inc];
			        tmpindex[shj]=tmpindex[shj-inc];
			        shj-=inc;
			        if (shj<=inc) break;
			      }
			      tmpscr[shj]=shv;
			      tmpindex[shj]=shw;
			    }
		      } while (inc>1);
		      ct=0;
		      for (i=0; i<mvct-1; i++) {   //keep best 26
                
			    //if (tmpscr[i+1]>minscr && ct<*pmxor) {
				if (tmpscr[i+1]>minscr && ct<mxor[pmxor]) {
			      ct++;
			      j=tmpindex[i+1];
			      //*(pmvscr+i) = tmpscr[i+1];
				  
				  mvscr[getX(pmvscr+i, mvscr)][getY(pmvscr+i, mvscr)] = tmpscr[i+1];
				  
			      //*(pmvlst+i) = tmpmv[j];

				  mvlst[getX(pmvlst+i, mvlst)][getY(pmvlst+i, mvlst)] = tmpmv[j];
				  
				  for (ii=1; ii<=npl; ii++) {
			        mvsco[lvl][i][ii] = tmpsco[j][ii];
                  }
			    }
			    else break;
		      }
		      if (ct<1) {
			    ct=1;
			    j=tmpindex[1];
			    //*(pmvscr) = tmpscr[1];
				mvscr[getX(pmvscr, mvscr)][getY(pmvscr, mvscr)] = tmpscr[1];
			    //*(pmvlst) = tmpmv[j];
				mvscr[getX(pmvlst, mvlst)][getY(pmvlst, mvlst)] = tmpmv[j];
			    for (ii=1; ii<=npl; ii++)
			      mvsco[lvl][0][ii] = tmpsco[j][ii];
		      }
		      //if (ct<*pmxor) *pmxmv=ct;
			  if (ct<mxor[pmxor]) mxmv[pmxmv]=ct;
		      //else *pmxmv=*pmxor;
			  else mxmv[pmxmv]=mxor[pmxor];
		    } // end fl1

		    //(*pmv)++;       //next move


			mv[pmv]++;
			
		    //x=(*(pmvlst+(*pmv)))%size;
			x=mvlst[getX(pmvlst+mv[pmv], mvlst)]
				   [getY(pmvlst+mv[pmv], mvlst)]%size;

		    //y=(*(pmvlst+(*pmv)))/size;
			y=mvlst[getX(pmvlst+mv[pmv], mvlst)]
				   [getY(pmvlst+mv[pmv], mvlst)]/size;

		    wfl=0;
		    //sc=*(pmvscr + (*pmv));
			sc=mvscr[getX(pmvscr+mv[pmv], mvscr)]
				    [getY(pmvscr+mv[pmv], mvscr)];

		    //sco[fr]=mvsco[lvl][*pmv][fr]; //used in VCT
			sco[fr]=mvsco[lvl][mv[pmv]][fr]; //used in VCT

		    if (vct!=0) {   //VCT
		    //pexfl=&exfl[lvl];
			pexfl=lvl;
		    //pexel=&exel[lvl];
			pexel=lvl;
		    //*pexfl=exfl[lvl-1];
			exfl[pexfl]=exfl[lvl-1];
		    //*pexel=exel[lvl-1];
			exel[pexel]=exel[lvl-1];
		    ctfl=1;
		    //if (*pmxmv<2) ctfl=0;
			if (mxmv[pmxmv]<2) ctfl=0;
		    if (fr==cp && ctfl!=0) {
		      //if (sco[fr]<520 && *pexfl>1 && lvl>=mxst && lvl>mxvt[plv])
			  if (sco[fr]<520 && exfl[pexfl]>1 && lvl>=mxst && lvl>mxvt[plv])
			    //*pexfl=*pexfl-2;
				exfl[pexfl]=exfl[pexfl]-2;
		      //if (sco[fr]<110 && *pexfl>1 && lvl>=mxst)
			  if (sco[fr]<100 && exfl[pexfl]>1 && lvl>=mxst)
			    //*pexfl=*pexfl-2;
				exfl[pexfl]=exfl[pexfl]-2;
		      //if (sco[fr]<110 && (*pexfl)%2 && sc<1800) (*pexfl)--;
			  if (sco[fr]<110 && exfl[pexfl]%2!=0 && sc<1800) exfl[pexfl]--;
		    }
		    if (fr!=cp && ctfl!=0) {
		      //if (sco[fr]<520 && *pexel>1 && lvl>=mxst && lvl>mxvt[plv])
			  if (sco[fr]<520 && exel[pexel]>1 && lvl>=mxst && lvl>mxvt[plv])
			    //*pexel=*pexel-2;
				exel[pexel]=exel[pexel]-2;
		      //if (sco[fr]<110 && *pexel>1 && lvl>=mxst)
			  if (sco[fr]<110 && exel[pexel]>1 && lvl>=mxst)
			    //*pexel=*pexel-2;
				exel[pexel]=exel[pexel]-2;
		      //if (sco[fr]<110 && (*pexel)%2 && sc<1800) (*pexel)--;
			  if (sco[fr]<110 && exel[pexel]%2!=0 && sc<188) exel[pexel]--;
		    }
		    //if (*pexfl<2 && *pexel<2 && lvl>=mxvt[plv]) mxlv=lvl;
			if (exfl[pexfl]<2 && exel[pexel]<2 && lvl>=mxvt[plv]) mxlv=lvl;
		    //if (!(*pexfl) && !(*pexel) && lvl>=plv) mxlv=lvl;
			if (exfl[pexfl]==0 && exel[pexel]==0 && lvl>=plv) mxlv=lvl;
		    if (lvl<=plv) mxlv=plv;

		    //if ((*pexfl>1 || *pexel>1) && lvl==mxlv && lvl<mxvf[plv]) mxlv++;
			if ((exfl[pexfl]>1 || exel[pexel]>1) && lvl==mxlv && lvl<mxvf[plv]) mxlv++;
		    //if (((*pexfl)%2 || (*pexel)%2) && lvl==mxlv && lvl<mxvt[plv]) mxlv++;
			if ((exfl[pexfl]%2!=0 || exel[pexel]%2!=0) && lvl==mxlv && lvl<mxvt[plv]) mxlv++;
			
		    } //end vct
		    else mxlv=plv;
		    if (mxlv>ciel[cp][lvl]) mxlv=ciel[cp][lvl];


		    //if (lvl<mxlv && *pmv<*pmxmv) {
			if (lvl<mxlv && mv[pmv]<mxmv[pmxmv]) {

		      for (ii=0; ii<size; ii++) {  //copy board
			    //pbd1=bd[lvl][ii];
			    //pbd0=bd[lvl-1][ii];
			    //for (ij=0; ij<size; ij++) *(pbd1+ij)=*(pbd0+ij);
				for (ij=0; ij<size; ij++) bd[lvl][ii][ij]=bd[lvl-1][ii][ij];
		      }
		      for (ii=1; ii<=npl; ii++) cc[lvl][ii]=cc[lvl-1][ii];
		      bd[lvl][x][y]=fr;  // make move
		      for (xx=x-extnt; xx<x+1+extnt; xx++) {
			    //pbd1=bd[lvl][xx];
			    for (yy=y-extnt; yy<y+1+extnt; yy++) {
			      if (xx>=0 && xx<size && yy>=0 && yy<size)
			        //if (*(pbd1+yy)==0) *(pbd1+yy)=-1;
					if (bd[lvl][xx][yy]==0) bd[lvl][xx][yy]=-1;
                }
		      }
		      // chk capture
		      for (d=0; d<8; d++) {
			    c1=x+dx[d];
			    c2=y+dy[d];
			    c3=c1+dx[d];
			    c4=c2+dy[d];
			    c5=c3+dx[d];
			    c6=c4+dy[d];
			    c7=c5+dx[d];
			    c8=c6+dy[d];
			    if (c5>=0 && c5<size && c6>=0 && c6<size)
			      if (bd[lvl][c1][c2]>0 && bd[lvl][c3][c4]>0 &&
			      bd[lvl][c1][c2]!=fr && bd[lvl][c3][c4]!=fr) {
			        if (bd[lvl][c5][c6]==fr) {
			          cc[lvl][fr]+=2;
			          bd[lvl][c1][c2]=-1;
			          bd[lvl][c3][c4]=-1;
			        }
			        else {
			          if (c7>=0 && c7<size && c8>=0 && c8<size && game==2)
				        if (bd[lvl][c7][c8]==fr && bd[lvl][c5][c6]>0) {
				          cc[lvl][fr]+=3;
				          bd[lvl][c1][c2]=-1;
				          bd[lvl][c3][c4]=-1;
				          bd[lvl][c5][c6]=-1;
				        }
			        }
			      } // if en*2
		      }  // next d

		      if (sc>=10000)  { //check win
			    scr[lvl][fr]=12000-lvl;
			    scr[lvl][3-fr]=lvl-12000;

			    wfl=1;
			    if (lvl==1) {
			      scr[lvl][fr]=30000;
			      hmv[0]=mvlst[1][mv[1]];
			      wfl++;
			    }
		      }  // end win
		      //if (lvl==1 && *pmxmv==1) wfl=1;
			  if (lvl==1 && mxmv[pmxmv]==1) wfl=1;
		    } // end non-maxlv
		    fl1=1;
		  //} while (*pmv<*pmxmv && lvl<mxlv && !wfl);
		  } while (mv[pmv]<mxmv[pmxmv] && lvl<mxlv && wfl==0);

		  fl1=0;
		  //if (*pmv>=*pmxmv) { //no more moves
		  if (mv[pmv]>=mxmv[pmxmv]) { //no more moves
		    lvl--;
		    fr--;
		    if (fr==0) fr=npl;
		  }
		  else if (lvl==mxlv) {
		    hmv[lvl]=mvlst[1][mv[1]];
				  
		    if (sc>10000) {  // win
			  scr[lvl][fr]=12000-lvl;
			  scr[lvl][3-fr]=lvl-12000;
		    } //end win
		    else {
		      for (i=1; i<=npl; i++) sco[i]=0; //add up scores
		      for (ii=1; ii<=lvl; ii++)
			    for (i=1; i<=npl; i++) {
			      sco[i]+=mvsco[ii][mv[ii]][i];
			      if (sco[i]>7800) sco[i]=7800;
			}
			en=3-fr;
			//scr[lvl][fr]=sco[fr]-sco[en]*4+rand()*6/32738;
			scr[lvl][fr]=sco[fr]-sco[en]*4+((int)(Math.random()*6));
			scr[lvl][en]=-scr[lvl][fr];
		      for (ii=1; ii<=npl; ii++) {
			    if (scr[lvl][ii]>10000) scr[lvl][ii]=10000;
			    if (scr[lvl][ii]<-10000) scr[lvl][ii]=-10000;
		      }
		    } // end else sco[fr]>10000
		  } // end if maxlv

		  if (wfl!=0) {
		    hmv[lvl]=mvlst[1][mv[1]];
		    mv[lvl]=mxmv[lvl];
		  }
		  
		  if(lvl!=0) {
			  
		  if (scr[lvl][fr] > scr[lvl-1][fr]) {
		    if (12000-scr[lvl][fr]<ciel[cp][lvl])
		      ciel[cp][lvl]=12000-scr[lvl][fr];
		    for (i=1; i<=npl; i++)
		      scr[lvl-1][i]=scr[lvl][i];
		    hmv[lvl-1]=hmv[lvl];
		  }
		  
		  }
		  
		  frmo=fr-1;
		  if (frmo<1) frmo+=npl;

		  if (lvl > 1)
		    if (scr[lvl][frmo] <= scr[lvl-2][frmo]) lvl--;
		  if (lvl==1 && scr[0][cp]<12000 && 12000-scr[0][cp]<ciel[cp][0])
		    ciel[cp][0]=12000-scr[0][cp];

		  lvl--;

		} while (lvl>=0 && wfl<2);

		bmove=hmv[0];

		if (bmove==0) bmove=mvlst[1][0];
		bscr=scr[0][cp];
		if (mxmv[1]==1) bscr=0;

		//System.out.println("end tree()");
		
		return 0;

	}


	int eval_s0, j, s[]=new int[7];
	int x9, y9, bl, tfr, tcap1;
	int eval(int x, int y)
        throws InterruptedException
	{
		eval_s0=0;
		
		lvl--;
		gf=0;
		eval_s0=score(x,y); //s0=0

		if (eval_s0>10000) {
		  for (j=1; j<=npl; j++) sco[j]=-12000;
		  sco[fr]=12000;
		}
		else {
		  for (j=1; j<=npl; j++) s[j]=sco[j];

		  if (cap1!=0) {
		  //if (cap1) {
		    gf=1;
		    x9=x; y9=y;
		    tfr=fr;
		    tcap1=cap1;
		    for (bl=1; bl<=tcap1; bl++ ) {
		      x=px[bl]; y=py[bl];
		      ppd=pd[bl];
		      if (ppd>4) ppd=ppd-4;
		    
			  fr=3-tfr;
			  eval_s0=score(x,y);
			  sco[1]=sco[1]-sco[1]/10;
			  sco[2]=sco[2]-sco[2]/10;
			  s[1]-=sco[1];
			  s[2]-=sco[2];

		    } // next bl
		    fr=tfr;
		    x=x9; y=y9;
		  }  //if capt

		  for (j=1; j<=npl; j++) {
		    sco[j]=s[j];
		    if (sco[j]>7800) sco[j]=7800;
		  }

		  eval_s0=sco[fr]-sco[3-fr]*4;

		  if (eval_s0>9500) eval_s0=9500;
		} //end else

		lvl++;
		return eval_s0;
	}

	// these are really local to score, but for performance
	// create the memory only once, score is called ALOT
	int dv, cx, cy, iw, qs, po, tys, hlim, g1, s0;
	int i, f0, side, sign, index, c4, c5;
	int f1[]=new int[2], sp[]=new int[2], la[]=new int[5], lb[]=new int[9], c2[]=new int[7], c3[]=new int[7], g[]=new int[2], lc[]=new int[5], ld[]=new int[9];
	int item, fl, lx, ly, df, iv, lf[]=new int[3];
	private int score(int x, int y)
        throws InterruptedException
	{
		// reset local vars to 0
		
		la[0]=la[1]=la[2]=la[3]=la[4]=0;
		lb[0]=lb[1]=lb[2]=lb[3]=lb[4]=lb[4]=lb[5]=lb[6]=lb[7]=lb[8]=0;
		c2[0]=c2[1]=c2[2]=c2[3]=c2[4]=c2[4]=c2[5]=c2[6]=0;
		g[0]=g[1]=0;
		lc[0]=lc[1]=lc[2]=lc[3]=lc[4]=0;
		ld[0]=ld[1]=ld[2]=ld[3]=ld[4]=ld[4]=ld[5]=ld[6]=ld[7]=ld[8]=0;
		lf[0]=lf[1]=lf[2]=0;
		
		//g values for O
		//0 ?.
		//1 ?X_
		//2 ?XX_
		//3 ?XXO
		//4 ?XXX_
		//5 ?XXXO
		//6 ?O_
		//7 ?OO_
		//8 ?_

		cap1=0;
		c4=c5=0;
		dv=0;
		for (i=1; i<7; i++) {
		  sco[i]=0;
		  c3[i]=0;
		}

		do { //c0
          
		  if (gf==1 && dv==ppd) {  //just need c4, c5
		    for (sign=-1; sign<2; sign+=2) {  //look for captures
              
              for (iw=1; iw<hlim; iw++) {
                
			    lc[iw]=0;
			    cx=x+dx[dv]*iw*sign;
			    cy=y+dy[dv]*iw*sign;
			    if (cx>=0 && cx<size && cy>=0 && cy<size) {
			      qs=bd[lvl][cx][cy];
			      if (qs>0) la[iw]=qs;
			      else la[iw]=0;
			    } else la[iw]=-1;
		      }  //iw
		      if (la[1]>0 && la[1]!=fr)
			    if (la[2]>0 && la[2]!=fr) { //b0
			      if (la[3]==fr) c4+=2;  //pair now open for capture
				  if (la[3]==0) c5+=2;
			      //if (!la[3]) c5+=2;     //pair no longer open for capture
			      if (la[3]>0 && la[3]!=fr && game==2) {
			        if (la[4]==fr) c4+=3;
					if (la[4]==0) c5+=3;
			        //if (!la[4]) c5+=3;
			      }
			    } // b0
		    } //sign
		  dv++;
		  }  //gf/dv

		  if (dv<4) {
		  for (sign=-1; sign<2; sign+=2) {  //look for captures
		    lf[sign+1]=-1;
		    for (iw=1; iw<hlim; iw++) {
		      lc[iw]=0;
		      cx=x+dx[dv]*iw*sign;  //first
		      cy=y+dy[dv]*iw*sign;
		      if (cx>=0 && cx<size && cy>=0 && cy<size) {
			    qs=bd[lvl][cx][cy];
			    if (qs>0) {
			      la[iw]=qs;
			      if (lf[sign+1]<0) lf[sign+1]=qs;
			    }
			    else la[iw]=0;
		      } else la[iw]=-1;
		    }  //iw
		    g1=0;
			if (la[1]==0) g1=8;
		    //if (!la[1]) g1=8;
		    if (la[1]==fr) {
			  if (la[2]==0) g1=6;
			  //if (!la[2]) g1=6;
			  if (la[2]==fr && la[3]==0) g1=7;
			  //if (la[2]==fr && !la[3]) g1=7;
		    }
		    if (la[1]>0 && la[1]!=fr) { //en
			  if (la[2]==0) g1=1;
		      //if (!la[2]) g1=1;
		      if (la[2]>0 && la[2]!=fr) { //b0
				if (la[3]==0) g1=2;
			    //if (!la[3]) g1=2;
				if (la[3]==fr && gf==0) {
			    //if (la[3]==fr && !gf) {
			      g1=3;
			      for (i=1; i<3; i++) {
			        cap1++;
			        px[cap1]=x+dx[dv]*sign*i;
			        py[cap1]=y+dy[dv]*sign*i;
			        pd[cap1]=dv;
			        pf[cap1]=la[i];
			        lc[i]=la[i];
			       la[i]=0;
			      }
			    }
			    if (la[3]==fr && gf==1) {
			      g1=0;
			      c4+=2;
			    }
			    if (la[3]>0 && la[3]!=fr && game==2) {
	 			  if (la[4]==fr && gf==0) {
			      //if (la[4]==fr && !gf) {
			        g1=5;
			        for (i=1; i<4; i++) {
			          cap1++;
			          px[cap1]=x+dx[dv]*i*sign;
			          py[cap1]=y+dy[dv]*i*sign;
			          pd[cap1]=dv;
			          pf[cap1]=la[i];
			          lc[i]=la[i];
			          la[i]=0;
			        }
			      }
			      if (la[4]==fr && gf==1) {
			        g1=0;
			        c4+=3;
			      }
			      //if (!la[4]) g1=4;
				  if (la[4]==0) g1=4;
			    }
		      } // b0
		    } // en
		    iw=0; if (sign>0) iw=1;
		    for (i=1; i<hlim; i++) {
		      lb[i+iw*4]=la[i];
		      ld[i+iw*4]=lc[i];
		    }
		    g[iw]=g1;

		  } //sign

		  for (i=1; i<=npl; i++) c2[i]=0;
		  sp[0]=sp[1]=0;
		  if (g[0]==8 || g[0]==3 || g[0]==5) sp[0]=1;
		  if (g[1]==8 || g[1]==3 || g[1]==5) sp[1]=1;
		  for (i=0; i<2; i++) {
 		    if (g[i]==6 && sp[1-i]!=0) { //pairs
		    //if (g[i]==6 && sp[1-i]) { // pairs
		      if (game==2) sco[fr]-=20;
		      else sco[fr]-=12;
		    }
			if (game==2 && g[i]==7 && sp[1-i]!=0) sco[fr]-=12;
		    //if (game==2 && g[i]==7 && sp[1-i]) sco[fr]-=12;
		    if (game==1 && g[i]==7) sco[fr]+=12;
		    if (g[i]==2) {               // threaten a pair
		      sco[fr]+=50;
		      if (cc[lvl][fr]+cap1>game*5+2)
		        sco[fr]+=1024;
		    }
		    if (game==2 && g[i]==4) {
		      sco[fr]+=75;
		      if (cc[lvl][fr]+cap1>game*5+1) sco[fr]+=1024;
		    }
		  } // next i
		  if (game==2 && g[0]==6 && g[1]==6) sco[fr]-=12; //pair

		  for (iw=0; iw<5; iw+=4) {                // O = played
            
		    for (i=1; i<=npl; i++) {                 // Z = potential capturer
		      if (i!=fr && lb[iw+1]>0 && lb[iw+2]>0)  // OXYZ
		        if (lb[iw+1]!=i && lb[iw+2]!=i) {     // protect
			      if (lb[iw+3]==i && (game==1 || lb[5-iw]!=0)) c3[i]+=2;
			      //if (lb[iw+3]==i && (game==1 || lb[5-iw])) c3[i]+=2;
			      if (game==2 && lb[iw+3]!=i && lb[iw+3]>0 && lb[iw+4]==i) c3[i]+=3;
		        }
		    } // next i
		    i=lb[5-iw];
		    if (i>0 && i!=fr)
		      if (lb[iw+1]>0 && lb[iw+1]!=i) {    // _XOZ
				if (lb[iw+2]==0) c2[i]+=2;			// make suscept. pair
		        //if (!lb[iw+2]) c2[i]+=2;          // make suscept. pair
				if (game==2 && lb[iw+2]>0 && lb[iw+2]!=i && lb[iw+3]==0) c2[i]+=3;
		        //if (game==2 && lb[iw+2]>0 && lb[iw+2]!=i && !lb[iw+3]) c2[i]+=3;
		      }
		    i=lb[6-iw];
		    if (i>0 && i!=fr)
		      if (lb[5-iw]>0 && lb[5-iw]!=i) {   // _OXZ
		        if (lb[iw+1]==0) c2[i]+=2;
				//if (!lb[iw+1]) c2[i]+=2;
		        if (game==2 && lb[iw+1]>0 && lb[iw+1]!=i && lb[iw+2]==0) c2[i]+=3;
		        //if (game==2 && lb[iw+1]>0 && lb[iw+1]!=i && !lb[iw+2]) c2[i]+=3;
		      }
		    i=lb[7-iw];
		    if (i>0 && i!=fr && game==2)       // _OXYZ
		      if (lb[6-iw]>0 && lb[6-iw]!=i && lb[5-iw]>0 && lb[5-iw]!=i)
		        if (lb[iw+1]==0) c2[i]+=3;
			    //if (!lb[iw+1]) c2[i]+=3;
		  } // next iw
		  for (i=1; i<=npl; i++) {
		    if (i!=fr) {            // c2 is stones now able to take
		      s0=c2[i]*25;
		      if (cc[lvl][i]+c2[i]>game*5+4) s0=s0+2048;
		      sco[i]+=s0;
		    }
		  } // next i

		// end capt

		po=0;
		f0=-1;

		do { //c1   //look up in table
		  f1[0]=f1[1]=-1;
		  index=0;
		  fl=sign=1;

		  if (lf[0]!=fr && lf[2]!=fr) //eval pre-capt
		    for (i=1; i<hlim+3; i++)
		      if (lb[i]==0 && ld[i]!=0 && i!=4) lb[i]=ld[i];
		      //if (!lb[i] && ld[i] && i!=4) lb[i]=ld[i];
		  do { //c2
            
		    //iw=*(table+index*4+0)*sign;
			iw=table[index*4+0]*sign;
		    side=1; iv=iw+4;
		    if (iw<0) { side=0; iv=-iw; }
		    qs=-2;
		    if (iw>-hlim && iw<hlim) qs=lb[iv];
		    if (qs<-1) {
		      cx=x+dx[dv]*iw;
		      cy=y+dy[dv]*iw;
		      if (cx>=0 && cx<size && cy>=0 && cy<size) {
			    qs=bd[lvl][cx][cy];
			    if (qs<0) qs=0;
		      }
		      else qs=-1;
		    }
		    if (qs>-1) {
		      if (qs>0) {
			    if (f0==-1) f0=qs;
			    if (f1[side]==-1) f1[side]=qs;
			    if (qs!=f0) {
			      item=1;
			      if (qs==f1[side]) po++;
			    }
			    else item=3;
		      }
		      else item=2;
		    }
		    else item=1;

			index=table[index*4+item];
		    //index=*(table+index*4+item);
		    if (index<0) {
		      index=-index;
		      sign=-sign;
		    }
		    if (index>9999) fl=0;
		  } while (fl!=0); //c2
//		  } while (fl); //c2
			
		  index-=10000;
		  //System.out.println("index="+index);
		  tys=scores[index*14+5];
		  //tys=*(scores+index*14+5);      //white pattern
		  if (f0==fr) fuk[x+y*size][dv]=tys;

		  //score for friend / enemy
		  if (f0==fr) {
			df=scores[index*14+2];
		    //df=*(scores+index*14+2);
		    sco[fr]+=df;
			if (npl==2 && gf==0) {
		    //if (npl==2 && !gf) {
		      if (tys==2 || tys==3)
				for (i=0; i<scores[index*14+7]; i++) {
                    
		        //for (i=0; i<*(scores+index*14+7); i++) {
		          fhole[fhn][2]=tys;
				  lx=x+dx[dv]*scores[index*14+8+i]*sign;
		          //lx=x+dx[dv]* *(scores+index*14+8+i)*sign;
				  ly=y+dy[dv]*scores[index*14+8+i]*sign;
		          //ly=y+dy[dv]* *(scores+index*14+8+i)*sign;
		          fhole[fhn][1]=lx+ly*size;
		          fhole[fhn][0]=mvct;
		          fhole[fhn][3]=dv;
		          fhn++;
		          if (fhn>749) {
		            fhn=749;
		            ferr++;
		          }
		        }  
		      if (tys==4 || tys==11 || tys==12)
				for (i=0; i<scores[index*14+7]; i++) {

		        //for (i=0; i<*(scores+index*14+7); i++) {
		          if (tys==4 && i<2 || tys==11 && i>1 || tys==12 && i<2)
			        fhole[fhn][2]=3;
			      else fhole[fhn][2]=2;
				  lx=x+dx[dv]*scores[index*14+8+i]*sign;
			      //lx=x+dx[dv]* *(scores+index*14+8+i)*sign;
				  ly=y+dy[dv]*scores[index*14+8+i]*sign;
			      //ly=y+dy[dv]* *(scores+index*14+8+i)*sign;
			      fhole[fhn][1]=lx+ly*size;
			      fhole[fhn][0]=mvct;
			      fhole[fhn][3]=dv;
			      fhn++;
			      if (fhn>749) {
			        fhn=749;
			        ferr++;
			      }
			    }
		    } //npl=2, !gf
		  } //friend
		  else {
			  //int ask=index*14+3;
			  //System.out.println(f0+", "+ask);

			  if(f0!=-1) {
				  sco[f0]-=scores[index*14+3];
			  }
		  }
		  //else sco[f0]-= *(scores+index*14+3);
		  if (f0==f1[0]) f0=f1[1];  //eval other player
		  else f0=f1[0];

		} while (po==1);  //c1

		dv++;
		} //!dv==4

		} while (dv<4 && sco[fr]<10000);    // c0

		for (i=1; i<=npl; i++) {
		  if (i!=fr) {
		    s0=-c3[i]*25; // c3 is stones now blocked from capture
		    if (cc[lvl][i]+c3[i]>game*5+4) s0=s0-1024;
		    sco[i]+=s0;
		  }
		} // next i

		sco[fr]+=cap1*160; // captures

		s0=(c4-c5)*25;         // c4 is stones now open for capture
		if (cc[lvl][fr]+c4>game*5+4) s0=s0+1024;
		if (cc[lvl][fr]+c5>game*5+4) s0=s0-1024;
		sco[fr]-=s0; //is subtracted in eval

		if (cc[lvl][fr]+cap1>game*5+4) sco[fr]=12000;

		if (sco[fr]>12000) sco[fr]=12000;
		s0=sco[fr];

		return s0;
	}
}
