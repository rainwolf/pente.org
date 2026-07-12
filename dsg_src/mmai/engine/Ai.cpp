// Synced from react_mmai/MMAIWASM at commit 03be73b.
// DO NOT EDIT HERE — edit upstream & re-sync
// via dsg_src/mmai/sync_from_upstream.sh.
//
#include <time.h>//for use of time() & symbolic constant NULL (also
#include <stdlib.h>//for use of rand() & srand() functions and
#include "Ai.h"
#include <iostream>
#include <fstream>
#include <string>

using namespace std;

#define HASH 1

int arc4random_uniform(int r) {
	if (r == 0) return 0;
	return rand()%r;
}

VariantConfig CAi::configFor(int gameId) {
	// Canonical server IDs (GridStateFactory): Pente=1, Keryo=3, Poof=11,
	// Connect6=13, Boat=15, O-Pente=25. Even IDs are Speed twins with the
	// same board rules. Legacy engine callers pass 2 for Keryo (kept for
	// backward compat with the current saga, shadowing canonical Speed-Pente).
	// Unknown/unmapped IDs (incl. 0, negatives, D-Pente=5, G-Pente=7...)
	// silently fall back to plain Pente rules below.
	int base = gameId;
	if (base == 2) base = 3;
	else if (base > 2 && base % 2 == 0) base -= 1;

	VariantConfig c;
	switch (base) {
		case 3: // Keryo-Pente
			c.captureTriples = true;
			c.capWinCount = 15;
			break;
		case 11: // Poof-Pente
			c.poofPairs = true;
			break;
		case 13: // Connect6: two-stone turns, 6-in-a-row win (overlines count), no captures
			c.capturePairs = false;
			c.winRowLength = 6;
			c.stonesPerTurn = 2;
			c.tournamentOpening = false;
			break;
		case 15: // Boat-Pente
			c.boatWin = true;
			break;
		case 25: // O-Pente
			c.captureTriples = true;
			c.capWinCount = 15;
			c.poofPairs = true;
			c.poofTriples = true;
			c.boatWin = true;
			break;
		case 1: // Pente
		default:
			break;
	}
	return c;
}

// Owner (1 or 2) of the stone placed at 0-based global move index idx.
// Connect6 turns are two stones (except the very first, a single stone at
// idx 0): the move sequence owner pattern is 1,2,2,1,1,2,2,1,... so
//   idx%4 in {0,3} -> P1, idx%4 in {1,2} -> P2.
// Every other variant strictly alternates (1,2,1,2,...).
// Only ever called on the Connect6 path (all call sites gate on
// stonesPerTurn==2), so the alternating branch stays dormant for the
// pente family and the goldens are unaffected.
int CAi::ownerOf(int idx) {
    if (cfg.stonesPerTurn == 2) return (idx%4==0 || idx%4==3) ? 1 : 2;
    return 1 + idx%2;
}

CAi::CAi(int game1, int lvl, bool openingBook1, const char *filesDir) {
	if (openingBook1) {
		this->obfl = 1;
	} else {
		this->obfl = 0;
	}
	this->level = lvl;
	this->cfg = configFor(game1);

	// Resource-directory seam: build the three data-file paths from filesDir
	// (defaults to "files"). Trailing '/' handled either way. WASM keeps "files"
	// so the emscripten embed lookup is byte-identical to before.
	std::string base(filesDir ? filesDir : "files");
	if (!base.empty() && base.back() != '/') base += '/';
	std::string tblPath = base + "pente.tbl";
	std::string scsPath = base + "pente.scs";
	std::string penPath = base + "opngbk.pen";

//     int x, y,z;
//     pAt = ATbl;
// 	ifstream file ("pente.tbl", ios::in|ios::binary|ios::ate);//[943][4] 
// 	if (file.is_open())
//     {
// 	  file.seekg (0, ios::beg);
//       for (x=0; x<3772; x++) 
// 	    file.read((char*)&ATbl[x], sizeof(short));

// 	  file.close();
//     }
// 	else
// 	{
// 		printf("can't open\n");
// 	}

//     pAs = AScr;	
// 	ifstream file2 ("pente.scs", ios::in|ios::binary|ios::ate);//[912][14]
// 	if (file2.is_open())
//     {
// 	  file2.seekg (0, ios::beg);
//       for (x=0; x<12768; x++)
//         file2.read((char*)&AScr[x], sizeof(short));
	  
// 	  file2.close();
//     }
// 	else
// 	{
// 		printf("can't open\n");
// 	}

// 	pFk=new int[1444];//361*4 //fukumi list
// 	//fukumi is a 4-3 threat:
// 	// .O.BO.
// 	// ...A..
// 	// ...O..
// 	// ...O..
// 	// ...X..
// 	 // if O moves to A, that threatens a win a B
// 	pFh=new int[3000];//750*4 //fukumi "holes"
// 	pTr=new int[363]; //temp total score list 
// 	pTm=new int[363]; //temp move list
// 	pTo=new int[2541]; //temp score list per each player
// 	pTi=new int[363]; // temp index
// 	erfl=0;
// 	a1=200; //for alpha/beta but not used currently
// 	b1=-200;

// #if HASH == 1
// 	//hash table
// 	if ((pHashY = new unsigned int[1000000]) == NULL) erfl=1;
// 	if ((pHashD = new unsigned char[1000000]) == NULL) erfl=1;
// 	if ((pHashS = new short int[2000000]) == NULL) erfl=1;
// 	if (erfl) exit(1);
// #endif

// 	srand( (unsigned)time( NULL ) );

// 	unsigned int seed=52356;
// 	for (int i=0; i<362; i++) { //generate pseudorandom table
// 		seed=(2416*seed+374441)%1771875; 
// 		TableX[i]=seed;
// 		seed=(2416*seed+374441)%1771875;
// 		TableY[i]=seed;
// 	}
// 	TableX[361]=2031; //SET lo
// 	TableY[361]=3201;
        obfl = 1;
        
        // obk = malloc(openingBookSize * 24 * sizeof(short int));
        obk = new short int[openingBookSize * 24];
        // table = malloc(tsize*4 * sizeof(short int));
        table = new short int [tsize*4];
        // scores = malloc(bsize*14 * sizeof(short int));
        scores = new short int [bsize*14];
       
        // pFk = malloc(1444 * sizeof(int));
        // pFh = malloc(3000 * sizeof(int));
        // pTr = malloc(363 * sizeof(int));
        // pTm = malloc(363 * sizeof(int));
        // pTo = malloc(2541 * sizeof(int));
        // pTi = malloc(363 * sizeof(int));
		pFk=new int[1444];//361*4 //fukumi list
		//fukumi is a 4-3 threat:
		// .O.BO.
		// ...A..
		// ...O..
		// ...O..
		// ...X..
		 // if O moves to A, that threatens a win a B
		pFh=new int[3000];//750*4 //fukumi "holes"
		pTr=new int[363]; //temp total score list 
		pTm=new int[363]; //temp move list
		pTo=new int[2541]; //temp score list per each player
		pTi=new int[363]; // temp index
        
// #if HASH == 1
//         pHashY = malloc(1000000 * sizeof(unsigned int));
//         pHashD = malloc(1000000 * sizeof(unsigned char));
//         pHashS = malloc(2000000 * sizeof(short int));
// #endif
		erfl = 0;
		#if HASH == 1
			//hash table
			if ((pHashY = new unsigned int[1000000]) == NULL) erfl=1;
			if ((pHashD = new unsigned char[1000000]) == NULL) erfl=1;
			if ((pHashS = new short int[2000000]) == NULL) erfl=1;
			if (erfl) exit(1);
		#endif
        
        reset();


        short *table1 = new short int [tsize*4];


		ifstream file (tblPath.c_str(), ios::in|ios::binary|ios::ate);//[943][4]
        unsigned char *fileDataArray;
        int fileCntr = 0;
		if (file.is_open()) {
			int filesize = file.tellg();
			// printf("pente tbl size %d \n", filesize);
			file.seekg (0, ios::beg);
   //    for (int x=0; x<3772; x++) file.read((char*)&table1[x], sizeof(short));
			// file.seekg (0, ios::beg);
			fileDataArray = new unsigned char[3772*2];
			file.read((char*)fileDataArray, 3772*2);
	        for(int i=0; i<943; i++) {
	            for(int j=0; j<4; j++) {
	                if (fileCntr < filesize - 1) {
	                    table[i*4+j]= (((short int) fileDataArray[fileCntr + 1]) << 8) | fileDataArray[fileCntr];
	                    fileCntr += 2;
	                }
	                //System.out.println(table[i*4+j]);
	            }
	        }
	        pAt = table;
			file.close();
			delete[] fileDataArray;
		} else {
			printf("can't open pente tbl\n");
			loadErr = 1;
		}
        // for(int i = 3600; i<3650; i++) {
        //     printf("kitten %d\n", pAt[i]);
        // }
        // for (int i=0; i<3772; ++i) {
        // 	if (table1[i] != pAt[i])
        // 	{
	       //      printf("kittyyyyy %d: %d != %d\n", i, table1[i], pAt[i]);
        // 	}
        // }


		ifstream file2 (scsPath.c_str(), ios::in|ios::binary|ios::ate);
        fileCntr = 0;
		if (file2.is_open()) {
			int file2size = file2.tellg();
			file2.seekg (0, ios::beg);
      // for (int x=0; x<12768; x++) file2.read((char*)&scores[x], sizeof(short));
			fileDataArray = new unsigned char[12768*2];
			file2.read((char*)fileDataArray, 12768*2);
	        for(int i=0; i<912; i++) {
	            for(int j=0; j<14; j++) {
	                if (fileCntr < file2size - 1) {
	                    scores[i*14+j] = (((short int) fileDataArray[fileCntr + 1]) << 8) | fileDataArray[fileCntr];
	                    fileCntr += 2;
	                }
	            }
	        }
	        pAs = scores;
        	// for (int i = 80; i < 100; ++i)
        	// {
	        // 	printf("table %d \n", fileDataArray[i]);
        	// }
			file2.close();
			delete[] fileDataArray;
		} else {
			printf("can't open pente scs\n");
			loadErr = 1;
		}

        
		ifstream file3 (penPath.c_str(), ios::in|ios::binary|ios::ate);
        fileCntr = 0;
		if (file3.is_open()) {
			int file3size = file3.tellg();
			file3.seekg (0, ios::beg);
			fileDataArray = new unsigned char[file3size];
			file3.read((char*)fileDataArray, file3size);

	        obsize=(((int) fileDataArray[fileCntr + 1]) << 8) | fileDataArray[fileCntr];
	//        NSLog(@"kitty %i", obsize);
	        fileCntr += 2;
	        if(obsize < openingBookSize) {
	            cob=0;
	            int i=0, sint=0, ef=0;
	            
	            do {
	                i=cob;
	                if (fileCntr < file3size - 1) {
	                    sint=(((short int) fileDataArray[fileCntr + 1]) << 8) | fileDataArray[fileCntr];
	                    ef = sint;
	                } else {
	                    sint = -1;
	                    ef = -1;
	                }
	                fileCntr += 2;
	                nom[i]=sint;
	                if(ef!=-1) {
	                    fileCntr += 2;
	                    if (fileCntr < file3size - 1) {
	                        sint=(((short int) fileDataArray[fileCntr + 1]) << 8) | fileDataArray[fileCntr];
	                        ef = sint;
	                    } else {
	                        sint = -1;
	                        ef = -1;
	                    }
	                    fileCntr += 2;
	                    oscr[i]=sint;
	                    //System.out.println(sint);
	                    for(int j=0; j<nom[i]; j++) {
	                        if (fileCntr < file3size - 1) {
	                            sint=(((short int) fileDataArray[fileCntr + 1]) << 8) | fileDataArray[fileCntr];
	                            ef = sint;
	                        } else {
	                            sint = -1;
	                            ef = -1;
	                        }
	                        fileCntr += 2;
	                        //System.out.println(sint);
	                        obk[i*24+j]=sint;
	                    }
	                    cob++;
	                }
	            } while(ef!=-1 && cob<obsize);
	        }

			file3.close();
			delete[] fileDataArray;
		} else {
			printf("can't open opngbk pen\n");
			loadErr = 1;
		}
}

CAi::~CAi()
{
	delete[] obk;
	delete[] table;
	delete[] scores;

	delete[] pFk;
	delete[] pFh;
	delete[] pTr;
	delete[] pTm;
	delete[] pTo;
	delete[] pTi;

#if HASH == 1
	delete[] pHashY;
	delete[] pHashD;
	delete[] pHashS;
#endif
}

void CAi::reset() {
    for (int i = 0; i < 362; i++) {
        sx[i] = 0;
        sy[i] = 0;
    }
    
    for (int x=0; x<size; x++)   //clear board at beginning
        for (int y=0; y<size; y++)
            brd[0][x][y]=0;
    ciel[1][0]=24;  //set cieling of search to max
    ciel[2][0]=24;
    ccc[0][1]=0; //number of captures for p1 (at level 0)
    ccc[0][2]=0; // "" p2
                //vct=0;   //no vct search
    obfl=1;  //opening book on
    extnt=2; //extent 2
    tn=0;
    moveNum=0;
    
    a1=200; //for alpha/beta but not used currently
    b1=-200;

    unsigned int seed=52356;
    for (int i=0; i<362; i++) { //generate pseudorandom table
        seed=(2416*seed+374441)%1771875;
        TableX[i]=seed;
        seed=(2416*seed+374441)%1771875;
        TableY[i]=seed;
    }
    TableX[361]=2031; //SET lo
    TableY[361]=3201;

    // Connect6: no opening book, no tournament ring, no pente openings. reset()
    // is the constructor tail (called after cfg is set and after obfl was forced
    // to 1 above / in the ctor), so clearing obfl here defeats dmov()'s and
    // cmove()'s book-matching blocks, which all key off obfl.
    if (cfg.stonesPerTurn == 2) obfl = 0;
    c6FallbackHits = 0;
    stopfl = 0;   // mobile stop seam cleared on every per-game reset
}


// void CAi::setUseOpeningBook(bool book) {
// 	if (book) {
// 		this->obfl = 1;
// 	} else {
// 		this->obfl = 0;
// 	}
// }
// void CAi::setLevel(int lvl) {
// 	this->level = lvl;
// }

// void CAi::Print(void)
// {
// 	   int x,y,sc;
//        for (y=0;y<19;y++) 
// 	   {
// 		  for (x=0;x<19;x++) 
// 		   {
// 		     if (bd[x][y]==-1) {
// 		       printf("C");
// 		     }
// 		     else printf("%d", bd[x][y]);
// 		   }
// 		  printf("\n");
// 	}
// }

void CAi::addMove(int move, int count) {
    sx[moveNum + 1] = move % size;
    sy[moveNum + 1] = move / size;
    
    tn = count;
    moveNum = count;
    dmov();
}

int CAi::getMove(int *moves, int count) {
	// Persistent-instance re-entrancy (mobile): the Android/iOS wrappers hold ONE
	// CAi across many moves, whereas WASM builds a fresh CAi per call. reset()
	// here re-derives every per-game accumulator (brd[0] board, ccc[] capture
	// counts, sx/sy, tn/moveNum, obfl/opening-book state, c6FallbackHits, stopfl)
	// from scratch, so a reused instance replaying a grown move list yields the
	// SAME result as a fresh instance. For the WASM fresh-per-call path this is a
	// second, idempotent reset() (the ctor already called it and nothing ran in
	// between) that consumes no rand() draws, so the goldens are unchanged.
	reset();
	for(int i = 0; i < count; i++) {
		addMove(moves[i], i+1);
	}
    moveNum = count;
    tn = moveNum + 1;

    if (cfg.stonesPerTurn == 2) {
        // ---- Connect6 two-stone turn (packed return) ----------------------
        // ownerOf() owner pattern by 0-based global index:
        //   idx : 0   1   2   3   4   5   6  ...
        //   own : P1  P2  P2  P1  P1  P2  P2 ...   (idx 0 = lone opening stone)
        // The stone about to be placed has index `count` (== tn-1). This turn
        // still has 2 stones to place iff the NEXT index belongs to the same
        // player; move 0 and any mid-pair invocation leave just 1:
        //   count=0: own(0)=1,own(1)=2 differ -> 1 (move 0, single stone)
        //   count=1: own(1)=2,own(2)=2 same   -> 2 (P2 turn start)
        //   count=2: own(2)=2,own(3)=1 differ -> 1 (P2 mid-pair)
        //   count=3: own(3)=1,own(4)=1 same   -> 2 (P1 turn start)
        //   count=4: own(4)=1,own(5)=2 differ -> 1 (P1 mid-pair)
        //   count=5: own(5)=2,own(6)=2 same   -> 2 (P2 turn start)
        int stonesRemaining = (ownerOf(count+1) == ownerOf(count)) ? 2 : 1;

        // Stone 1: normal search path. cmove() forces move 180 at tn==1 and
        // returns -1 for every later Connect6 turn (openings disabled), so the
        // Tree search in Move() runs.
        seat = ownerOf(count);
        int m1 = cmove();
        if (m1 == -1) m1 = Move();

        // Packed as m1*362+m2 (base 362, not 361): m2 in 0..360 is a real
        // cell, 361 is the single-stone sentinel. Base 361 was ambiguous --
        // m1*361+361 == (m1+1)*361+0 collided with the next m1's move-0 --
        // so the pack must use one more than the largest cell index.
        // Max packed value 360*362+361 = 130681, well within int range.
        if (stonesRemaining == 1)
            return m1 * 362 + 361;   // sentinel m2 == 361 (only one stone this turn)

        // Stone 2: replay m1 through the SAME addMove()/dmov() path used for the
        // input moves, so brd[0]/sx/sy/tn stay canonical and dmov() assigns it
        // to the same player (ownerOf(tn-1)==ownerOf(count)). Then search again;
        // the second search's root player is ownerOf(count+1) == ownerOf(count),
        // so ownerOf keeps the same player on the move. Two full searches per
        // turn is the deliberate v1 design (simple + correct); no PV/pair theory.
        addMove(m1, count+1);
        moveNum = count + 1;
        tn = moveNum + 1;            // count+2
        seat = ownerOf(count+1);
        int m2 = cmove();
        if (m2 == -1) m2 = Move();

        // Defensive guard: if the second search echoes m1 or lands on an
        // occupied/illegal cell, substitute any legal empty cell (adjacent to a
        // stone first, else any empty). Should never fire in normal play.
        if (m2 == m1 || m2 < 0 || m2 > 360 || brd[0][m2%19][m2/19] > 0) {
            c6FallbackHits++;
            int fb = -1;
            for (int yy=0; yy<19 && fb<0; yy++)
                for (int xx=0; xx<19; xx++) {
                    int mm = yy*19+xx;
                    if (mm==m1) continue;
                    if (brd[0][xx][yy]==-1) { fb=mm; break; }   // empty near a stone
                }
            if (fb < 0)
                for (int yy=0; yy<19 && fb<0; yy++)
                    for (int xx=0; xx<19; xx++) {
                        int mm = yy*19+xx;
                        if (mm==m1) continue;
                        if (brd[0][xx][yy]==0) { fb=mm; break; }   // any empty cell
                    }
            if (fb >= 0) m2 = fb;
        }
        return m1 * 362 + m2;
    }

    int move = cmove();
    if (move == -1) {
    	seat = 2 - tn%2;
        move = Move();
    }

    return move;
}

int CAi::Move() { // AI MAIN routine
    int i, x, y, icfl;
//    int om2[8] = {181,182,162,163,164,165,144,145};
//    int op2[8] = {25,36,77,82,93,95,97,99};
//    int om3[32] = {183,184,202,221,240,260,239,238,237,256,236,235,
//        234,252,215,196,177,176,158,139,120,100,
//        121,122,123,104,124,125,126,108,145,164};
    //opening moves for turn 2 and 3.
    //unsigned int mleft, avgt, tleft;
    
    
    dx[0]=dx[3]=dx[6]=-1;
    dy[0]=dy[1]=dy[2]=-1;
    dx[1]=dx[5]=dy[3]=dy[7]=0;
    dx[2]=dx[4]=dx[7]=1;
    dy[4]=dy[5]=dy[6]=1;
    rotx[0]=rotx[1]=rotx[2]=rotx[3]=1;
    rotx[4]=rotx[5]=rotx[6]=rotx[7]=-1;
    roty[0]=roty[1]=roty[6]=roty[7]=1;
    roty[2]=roty[3]=roty[4]=roty[5]=-1;
    rotf[0]=rotf[2]=rotf[4]=rotf[6]=0;
    rotf[1]=rotf[3]=rotf[5]=rotf[7]=1;
    mxnd[1]=20; mxnd[2]=18; //number of nodes to expand at each level
    mxnd[3]=16; mxnd[4]=14;
    mxnd[5]=mxnd[6]=mxnd[7]=mxnd[8]=mxnd[9]=12;
    mxnd[10]=mxnd[11]=mxnd[12]=mxnd[13]=mxnd[14]=mxnd[15]=8;
    mxnd[16]=mxnd[17]=mxnd[18]=8;
    mxnd[0]=0;
    
    //pView=pDoc->pView;
    gf=0;
    multipbem=0;
    np=2;
    
    /////////////input from somewhere ....//////////////////////////
    cp=seat; //current player
    turn=tn; //current turn
    tnRoot=(int)tn; //Connect6: base for Tree()'s ownerOf rotation (tn at Move() entry)
    for (x=0; x<19; x++)
        for (y=0; y<19; y++)
            bd[x][y]=brd[0][x][y]; //BOARD
    cc[0][1]=ccc[0][1]; //current captured pieces p1
    cc[0][2]=ccc[0][2]; //current captured pieces p1

    // Boat: the real last move (sx[tn-1],sy[tn-1]) belongs to the opponent and
    // maps to search slot 0. fr at even search levels equals that opponent
    // (fr=cp-1+lvl wrapped, np==2 -> fr(lvl2)=3-cp), so a five it left standing
    // is enforced by Tree()'s lvl>=2 survived check. Zero every slot first so no
    // pending state leaks between getMove() calls (cheap, keeps non-boat clean).
    for (i=0; i<20; i++) pendN[i]=0;
    // np==2 guard matches Tree()'s lvl>=2 survived check: np is set to 2
    // just above and nothing between there and here can change it, but the
    // guard is kept for consistency since lp=3-cp only makes sense for 2
    // players (a future >2-player np would make it nonsense).
    if (cfg.boatWin && np==2 && tn>=2) {
        int lx=sx[tn-1], ly=sy[tn-1], lp=3-cp; // opponent = 3-cp
        if (lx>=0 && lx<19 && ly>=0 && ly<19 && bd[lx][ly]==lp)
            pendN[0]=boatRun(lx, ly, lp, pendC[0]);
    }

    plv=level;  //depth of search 1-12
//    gf=pDoc->gf;  //set to 0
                  ///////////////////////////////////////////////////////////////
    
    vct = (cfg.stonesPerTurn==2) ? 0 : 1; //threat search (Connect6 v1: no VCT/fukumi/threat extensions)
    tourn=cfg.tournamentOpening ? 1 : 0; //tournament rule
    breadth=1;
    extent=0;
    //int mxvt[13]={0,1,3,4,6,7,8,9,10,12,13,14,15};
    //int mxvf[13]={0,1,4,5,7,8,10,11,13,15,16,17,18};
    mxvt[0]=mxvf[0]=0; //limits for vct
    mxvt[1]=mxvf[1]=1;
    mxvt[2]=3;
    mxvt[3]=mxvf[2]=4;
    mxvf[3]=5;
    mxvt[4]=6;
    mxvt[5]=mxvf[4]=7;
    mxvt[6]=mxvf[5]=8;
    mxvt[7]=9;
    mxvt[8]=mxvf[6]=10;
    mxvf[7]=11;
    mxvt[9]=12;
    mxvt[10]=mxvf[8]=13;
    mxvt[11]=14;
    mxvt[12]=mxvf[9]=15;
    mxvf[10]=16;
    mxvf[11]=17;
    mxvf[12]=18;
    for (i=13; i<19; i++) { mxvf[i]=18; mxvt[i]=18; }
    
    ciel[0][1]=ciel[0][2]=24;
    
    bmove=0;
    bscr=0;
    exfl[0]=3;
    exel[0]=3;
    mxst=2;
    srand((unsigned)time(NULL));
    
    if (gf==3) //just score pt
    {
        gf=0;
        lvl=0;
        for (i=0; i<1444; i++)    //clear fukumi/legal table
            *(pFk+i)=0;
        fr=cp;
        c6NextSame=0;   // single-point score has no same-turn follower: never a win-in-pair
        fhn=0;
        bscr= Score(pxy[0]);
//        pDoc->cap1=cap1;           //return vals
        for (x=0; x<cap1; x++)
            pxy[x]=p1xy[x];
        return 0;
    }
    if (turn==1) bmove=180;
    if (turn==2 && cfg.stonesPerTurn!=2) {   // Connect6: no tn==2 pente opening -> search
        do {
            x=7+rand()%5;
            y=7+rand()%5;
        } while (bd[x][y]>0);
        bmove=y*19+x;
    }
    if (turn==3 && cfg.stonesPerTurn!=2) {   // Connect6: no tn==3 pente opening -> search
        do {
            i=rand()%32;
            x=om3[i]%19;
            y=om3[i]/19;
            icfl=bd[x][y];
        } while (icfl>0);
        bmove=y*19+x;
    }
    
    maxscr=32000;
    
    if (!bmove) {
        alpha=32000;
        beta=-32000;
        plv= Tree();
        x=1;
    } 
    
//    pDoc->bscr=bscr; //output best score
//    pDoc->bmove=bmove; //output best move (y*19 + x)
    
    return bmove;
}





void CAi::dmov() {
    int i, j, k, x, y, cx, cy, obi, mfl, kfl;
    int c1,c2,c3,c4,c5,c6,c7,c8,d;
    
    // Current player owning the replayed stone. addMove() places stone #tn
    // (1-based turn), whose 0-based global index is tn-1, so the owner is
    // ownerOf(tn-1). Connect6 needs this (its turns are two stones); the pente
    // family keeps the exact old 2-tn%2 alternation (goldens gate).
    cp = (cfg.stonesPerTurn==2) ? ownerOf((int)tn-1) : 2-tn%2;  //set current player
    brd[0][sx[tn]][sy[tn]]=cp;  //place piece (1 or 2) on board
    for (x=sx[tn]-extnt; x<sx[tn]+1+extnt; x++) //set spaces around piece to -1
        for (y=sy[tn]-extnt; y<sy[tn]+1+extnt; y++) //for consideration by ai
            if (x>=0 && x<size && y>=0 && y<size)
                if (brd[0][x][y]==0) brd[0][x][y]=-1;
    
    // chk poof (Poof-Pente/O-Pente): the placed stone plus its own neighbors get
    // sandwiched by enemies and vanish. Every poof form (pair + triple end/center)
    // is detected against the pre-removal board, collected, then applied -- this
    // mirrors the server's addMove, which stashes all hits in tempPoofed before
    // applying any; with triples added a scan-and-remove pass could disturb a
    // later form's neighbors. Credit follows GameClass (the React app's referee,
    // authoritative for this app): #detectPoof (pair form) and #detectKeryoPoof
    // (triple form) each add their OWN +1 bonus once, if any of their directions
    // fired, on top of +1 per stone actually removed. removed_stones = deduped
    // neighbors (qn) + 1 for the placed stone. A pure pair or pure triple poof
    // fires only one form's bonus, so it still credits removed_stones (qn+1,
    // numerically unchanged by this fix). A MIXED pair+triple poof fires BOTH
    // forms' bonuses, so it credits removed_stones+1 (qn+2) -- one more than a
    // pure poof, matching GameClass's double per-form bonus. Formula: qn +
    // pairFired + tripleFired, each flag 0/1 for whether that form fired at all.
    // NOTE the server's OPenteState over-credits differently again: +2 per
    // firing DIRECTION (not per form) plus 1 for the placed stone, with no dedup,
    // so it can double-count shared cells on multi-direction poofs; that server
    // behavior is NOT mirrored here -- GameClass is authoritative for this app.
    // Either way the placed stone is still removed exactly once; only the CREDIT
    // arithmetic changes for the mixed case.
    if (cfg.poofPairs) {
        int qx[17], qy[17], qn=0, pairFired=0, tripleFired=0;
        auto qpush = [&](int px, int py) {   // collect a poofed neighbor, deduped
            if (qn>=17) return;
            for (int qi=0; qi<qn; qi++) if (qx[qi]==px && qy[qi]==py) return;
            qx[qn]=px; qy[qn]=py; qn++;
        };
        for (d=0; d<8; d++) {                            // pair form: own nbr at +1d
            c1=sx[tn]+dx[d];   c2=sy[tn]+dy[d];          //   own neighbor  (+1d)
            c3=c1+dx[d];       c4=c2+dy[d];              //   far enemy flank (+2d)
            c5=sx[tn]-dx[d];   c6=sy[tn]-dy[d];          //   near enemy flank(-1d)
            if (c3>=0 && c3<size && c4>=0 && c4<size &&
                c5>=0 && c5<size && c6>=0 && c6<size)
                if (brd[0][c1][c2]==cp &&
                    brd[0][c3][c4]>0 && brd[0][c3][c4]!=cp &&
                    brd[0][c5][c6]>0 && brd[0][c5][c6]!=cp) {
                    qpush(c1, c2);
                    pairFired=1;
                }
        }
        if (cfg.poofTriples) {
            for (d=0; d<8; d++) {                        // end form: own at +1d,+2d
                c1=sx[tn]+dx[d];     c2=sy[tn]+dy[d];        //   own    (+1d)
                c3=sx[tn]+2*dx[d];   c4=sy[tn]+2*dy[d];      //   own    (+2d)
                c5=sx[tn]+3*dx[d];   c6=sy[tn]+3*dy[d];      //   enemy  (+3d)
                c7=sx[tn]-dx[d];     c8=sy[tn]-dy[d];        //   enemy  (-1d)
                if (c5>=0 && c5<size && c6>=0 && c6<size &&
                    c7>=0 && c7<size && c8>=0 && c8<size)
                    if (brd[0][c1][c2]==cp && brd[0][c3][c4]==cp &&
                        brd[0][c5][c6]>0 && brd[0][c5][c6]!=cp &&
                        brd[0][c7][c8]>0 && brd[0][c7][c8]!=cp) {
                        qpush(c1, c2); qpush(c3, c4);
                        tripleFired=1;
                    }
            }
            for (d=0; d<4; d++) {                        // center form: own at +1a,-1a
                c1=sx[tn]+dx[d];     c2=sy[tn]+dy[d];        //   own    (+1a)
                c3=sx[tn]-dx[d];     c4=sy[tn]-dy[d];        //   own    (-1a)
                c5=sx[tn]+2*dx[d];   c6=sy[tn]+2*dy[d];      //   enemy  (+2a)
                c7=sx[tn]-2*dx[d];   c8=sy[tn]-2*dy[d];      //   enemy  (-2a)
                if (c5>=0 && c5<size && c6>=0 && c6<size &&
                    c7>=0 && c7<size && c8>=0 && c8<size)
                    if (brd[0][c1][c2]==cp && brd[0][c3][c4]==cp &&
                        brd[0][c5][c6]>0 && brd[0][c5][c6]!=cp &&
                        brd[0][c7][c8]>0 && brd[0][c7][c8]!=cp) {
                        qpush(c1, c2); qpush(c3, c4);
                        tripleFired=1;
                    }
            }
        }
        if (qn) {                                        // apply: remove neighbors + placed
            for (int qi=0; qi<qn; qi++) brd[0][qx[qi]][qy[qi]]=-1;
            brd[0][sx[tn]][sy[tn]]=-1;
            ccc[0][3-cp]+=qn+pairFired+tripleFired;      // removed stones + per-form referee bonus
        }
    }

    // chk captures
    if (cfg.capturePairs) {
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
            if (brd[0][c1][c2]>0 && brd[0][c3][c4]>0 &&
                brd[0][c1][c2]!=cp && brd[0][c3][c4]!=cp) {
                if (brd[0][c5][c6]==cp) {
                    ccc[0][cp]+=2;
                    brd[0][c1][c2]=-1;
                    brd[0][c3][c4]=-1;
                }
                else {
                    if (c7>=0 && c7<size && c8>=0 && c8<size && cfg.captureTriples)
                        if (brd[0][c7][c8]==cp && brd[0][c5][c6]>0) {
                            ccc[0][cp]+=3;
                            brd[0][c1][c2]=-1;
                            brd[0][c3][c4]=-1;
                            brd[0][c5][c6]=-1;
                        }
                }
            } // if en*2
    }  // next d
    } // if capturePairs

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
                if (p[3-cp]!=0 && mfl!=0 && ((cp==2 && oscr[obi]>5) || (cp==1 && oscr[obi]<5)))
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
                    if (p[3-cp]!=0 && mfl!=0 && ((cp==2 && oscr[obi]>5) || (cp==1 && oscr[obi]<5)))
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
            i=arc4random_uniform(rlct-1);
            //i=rand()*(rlct-1)/32768;
            cob=rlst[i];
            crot=rrot[i];
        }
    } // if turn>1
    if (tn>4 && tn>=nom[cob]) {
        obfl=0;
    }

}


int CAi::cmove() {
    //System.out.println("start cmov() obfl="+obfl+",tn="+tn+",s="+size);
    
    int i, x, y, xx,t;

    //opening moves for turn 2 and 3.

    // Connect6: player from the global index (tn-1); pente family: old alternation.
    cp = (cfg.stonesPerTurn==2) ? ownerOf((int)tn-1) : 2-tn%2;
//    cp=seat;
    bmove=0;
    bscr=0;
    
    //        hlim=4;
    //        if (game==2) hlim=5;
    
    if (tn==1) bmove=180;
    if (tn==2 && obfl!=0) {
        //System.out.println("1");
        //if (tn==2 && obfl) {
        t = arc4random_uniform(99);
        //x=rand()*99/32768;
        i=-1;
        do i++;
        while (t>op2[i]);
        x=om2[i]%size-size/2;
        y=om2[i]/size-size/2;
        ////System.out.println(i+","+t+","+x+","+y);
        if (arc4random_uniform(2) == 1) x=-x;
        if (arc4random_uniform(2) == 1) y=-y;
        if (arc4random_uniform(2) == 1) { xx=x; x=y; y=xx; }
        //if (rand()%2) x=-x;
        //if (rand()%2) y=-y;
        //if (rand()%2) { xx=x; x=y; y=xx; }
        bmove=(y+size/2)*size+x+size/2;
        ////System.out.println(x+","+y+","+bmove);
        //        if (bmove == 84) {
        //            //System.out.println("84 " + i);
        //        }
        i=0;
    }
    else if (tn==2 && cfg.stonesPerTurn!=2) {   // Connect6: no tn==2 pente opening
        //System.out.println("2");
        do {
            x=7+(arc4random_uniform(3));
            y=7+(arc4random_uniform(3));
            //x=7+rand()/8192; //0-3
            //y=7+rand()/8192;
        } while (brd[0][x][y]>0);
        bmove=y*size+x;
    }
    if (tn==3 && obfl==0 && cfg.stonesPerTurn!=2) {   // Connect6: no tn==3 pente opening
        //System.out.println("3");
        //if (tn==3 && !obfl) {
        do {
            i=arc4random_uniform(31);
            //i=rand()*31/32768;
            x=om3[i]%size;
            y=om3[i]/size;
        } while (brd[0][x][y]>0);
        bmove=y*size+x;
    }
    if (obfl!=0 && tn>2) {
        //System.out.println("4");
        //if (obfl && tn>2) {
        if ( arc4random_uniform(99) < tn*6-23 ) {
            //System.out.println("5");
            obfl=0;
            //System.out.println("turn off opening book 3");
        }
        //if (rand()*99/32768 < tn*6-23) obfl=0;
        else {
            //System.out.println("6");
            x=obk[cob*24+tn-1]%size-size/2;
            //x=*(obk+cob*24+tn-1)%19-9;
            y=obk[cob*24+tn-1]/size-size/2;
            //y=*(obk+cob*24+tn-1)/19-9;
            if (rotf[crot]!=0) { xx=x; x=y; y=xx; }
            //if (rotf[crot]) { xx=x; x=y; y=xx; }
            x=x*rotx[crot]-xoff;
            y=y*roty[crot]-yoff;
            if (tn==4 && brd[0][x][y]>0) {   // flip moves 2 and 4
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
    /*
     if (bmove==0) {
     //System.out.println("no opening book, search");
     //if (!bmove) {
     plv=p[cp];
     tree();
     } // end if hmv=0;
     */
    if (bmove==0) {
        
        //System.out.println("7");
        bmove=-1;//indicates not opening book
    }
    if (bscr<11000) ciel[cp][0]=24;
    
    //lvl=0;
    
    //System.out.println("bmove="+bmove);
    
    //System.out.println("end cmov()");
    
    return bmove;
}




int CAi::Tree() {
    int minscr, ctfl, mxlv, mxor[19];
    int mv[19], mvsco[19][363][7], mvscr[19][363], mvlst[19][363];
    int scr[19][7], hmv[19], mxmv[19], exstkx[19][36], exstky[19][36];
    // capx/capy/capv widened 24->48: an O-Pente move can mix per-direction
    // captures and poofs across directions (e.g. some dirs capture-3, others
    // poof-2), so the shared undo stack can exceed the old 24-push worst case.
    int nstk[19], ncap[19], capx[19][48], capy[19][48], capv[19][48];
    // scr[0] is the computers final score after the search
    // hmv is the best move found in the format x+y*19
    int *pmvscr, *pmv, *pmxmv, *pmvlst, *pmxor, *pexfl, *pexel;
    int fl1, wfl, ii, x, y, i, j, frmo, rxy, rx, ry, hfl;
    int xx, yy, d, c1, c2, c3, c4, c5, c6, c7, c8, ct, sc;
    int shi, shj, shv, shw, inc, loc, tyt, tyf, tys;
    int htempx, cpx, cpy, cutfl;

    unsigned int htempy;
    // Boat: hash-key fold constants for the pending-run bits (Finding 1).
    // Large odd constants, deliberately NOT TableX/TableY slots -- kept
    // independent of the capture-count fold's table-driven constants.
    const unsigned int PENDBIT1=2654435761u, PENDBIT2=2246822519u;
    unsigned int pfold;
    int pb1, pb2;
    
    hmv[0]=0;
    mvlst[1][0]=-1;
    wfl=0;
    lvl=0;
    cutfl=1;
    sec[0]=0;
    //pDoc->Esc = FALSE;
    fl1=1;
    extnt=extent+2;
    for (i=1; i<19; i++) {
        mxor[i]=mxnd[i];
        if (breadth==2)
            mxor[i]=mxor[i]*2;
    }
    
#if HASH == 1
    HValX[0]=HValY[0]=0; //init hash pos
    for ( x=0; x<19; x++ )
        for ( y=0; y<19; y++)
            if (bd[x][y]==1 || bd[x][y]==2) {
                HValX[0] ^= (bd[x][y]*TableX[x+19*y]);
                HValY[0] ^= (bd[x][y]*TableY[x+19*y]);
            }
    for (x=0; x<1000000; x++) *(pHashD+x)=0;
#endif
    
    do { //A
        do { //B
            lvl++;
            pmvscr=mvscr[lvl];
            pmv=&mv[lvl];
            pmxmv=&mxmv[lvl];
            pmvlst=mvlst[lvl];
            pmxor=&mxor[lvl];
            
#if HASH == 1
            HValX[lvl]=HValX[lvl-1];
            HValY[lvl]=HValY[lvl-1];
#endif

            for (x=1; x<=np; x++)
                cc[lvl][x]=cc[lvl-1][x];
            if (cfg.stonesPerTurn==2) {
                // Connect6: rotate the player from the global stone index, not
                // by strict alternation. The first search ply (lvl==1) places
                // global index tnRoot-1, so fr = ownerOf(tnRoot-2+lvl).
                // Consecutive plies may share a player (a turn's two stones) --
                // exactly what the minimax needs. fr(lvl==1)==cp (==ownerOf of
                // the mover), keeping cp-indexed ciel/scr consistent.
                fr = ownerOf(tnRoot-2+lvl);
                // Win-in-pair gate for Score6: the stone placed here has global
                // index g = tnRoot-2+lvl; a same-turn second stone still follows
                // iff ownerOf(g+1)==fr. (Set right beside fr so every Eval/Score6
                // in this level's candidate scan sees the mover-consistent value.)
                c6NextSame = (ownerOf(tnRoot-1+lvl)==fr);
            } else {
                fr=cp-1+lvl;
                while (fr>np) fr-=np;
            }
            en=fr+1;
            if (en>np) en=1;
            if (fl1) {
                for (i=1; i<=np; i++) {
                    ciel[lvl][i]=ciel[lvl-1][i];
                    scr[lvl-1][i]= beta;
                }
                for (i=0; i<1444; i++) //clear fukumi table
                    *(pFk+i)=0;
                fhn=0;
                ferr=0;
                *pmv=-1;
                mvct=1;
                
                for (i=0; i<*pmxor; i++) *(pmvscr+i)=-30000;
                minscr=-30000;
                
                for (x=0; x<19; x++)
                    for (y=0; y<19; y++)
                        if (bd[x][y]==-1) {
                            sc = Eval(x,y);
                            if (np==2)
                                if (-sco[3-fr]>800 && minscr<3000)
                                    minscr=3000; //block four
                            *(pTr+mvct)=sc;
                            *(pTm+mvct)=y*19+x;
                            for (ii=1; ii<=np; ii++)
                                *(pTo+mvct*7+ii)=sco[ii];
                            *(pTi+mvct)=mvct;
                            mvct++;
                        } // end if spc
                for (j=0; j<fhn; j++) { //add in fukumi
                    loc=*(pFh+j*4+1);
                    tyt=tyf=0;
                    for (i=0; i<4; i++)
                        if (i!=*(pFh+j*4+3)) {
                            tys=*(pFk+loc*4+i);
                            if (tys==6) tyt++;
                            if (tys==5) tyt++;
                            if (tys==7 || tys==8) tyf++;
                        }
                    tys=*(pFh+j*4+2);
                    if (tys==2 && tyf) *(pTr+*(pFh+j*4))+=100;
                    if (tys==3 && tyt) *(pTr+*(pFh+j*4))+=100;
                    if (tys==3 && tyf) *(pTr+*(pFh+j*4))+=100;
                    if (tys==2 && tyt) *(pTr+*(pFh+j*4))+=50;
                }
                
                mvct--;
                inc=1; //shell sort
                do { inc*=3; inc++; }
                while (inc<=mvct);
                do {
                    inc/=3;
                    for (shi=inc+1; shi<=mvct; shi++) {
                        shv=*(pTr+shi);
                        shw=*(pTi+shi);
                        shj=shi;
                        while (*(pTr+shj-inc)<shv) {
                            *(pTr+shj)=*(pTr+shj-inc);
                            *(pTi+shj)=*(pTi+shj-inc);
                            shj-=inc;
                            if (shj<=inc) break;
                        }
                        *(pTr+shj)=shv;
                        *(pTi+shj)=shw;
                    }
                } while (inc>1);
                ct=0;
                for (i=0; i<mvct-1; i++) { //keep best ~26
                    if (*(pTr+i+1)>minscr && ct<*pmxor) {
                        ct++;
                        j=*(pTi+i+1);
                        *(pmvscr+i) = *(pTr+i+1);
                        *(pmvlst+i) = *(pTm+j);
                        for (ii=1; ii<=np; ii++)
                            mvsco[lvl][i][ii] = *(pTo+j*7+ii);
                    }
                    else break;
                }
                if (ct<1 && mvct>0) {
                    ct=1;
                    j=*(pTi+1);
                    *(pmvscr) = *(pTr+1);
                    *(pmvlst) = *(pTm+j);
                    for (ii=1; ii<=np; ii++)
                        mvsco[lvl][0][ii] = *(pTo+j*7+ii);
                    //errmsg(3);
                }
                if (ct<*pmxor) *pmxmv=ct;
                else *pmxmv=*pmxor;
            } // end fl1
            
            (*pmv)++; //next move
            x=(*(pmvlst+(*pmv)))%19;
            y=(*(pmvlst+(*pmv)))/19;
            wfl=0;
            sc=*(pmvscr + (*pmv));
            sco[fr]=mvsco[lvl][*pmv][fr]; //used in VCT
            
            if (vct) { //VCT
                pexfl=&exfl[lvl];
                pexel=&exel[lvl];
                *pexfl=exfl[lvl-1];
                *pexel=exel[lvl-1];
                ctfl=1;
                if (*pmxmv<2) ctfl=0;
                if (fr==cp && ctfl) {
                    if (sco[fr]<520 && *pexfl>1 && lvl>=mxst && lvl>mxvt[plv])
                        *pexfl=*pexfl-2;
                    if (sco[fr]<110 && *pexfl>1 && lvl>=mxst)
                        *pexfl=*pexfl-2;
                    if (sco[fr]<110 && (*pexfl)%2 && sc<1800) (*pexfl)--;
                }
                if (fr!=cp && ctfl) {
                    if (sco[fr]<520 && *pexel>1 && lvl>=mxst && lvl>mxvt[plv])
                        *pexel=*pexel-2;
                    if (sco[fr]<110 && *pexel>1 && lvl>=mxst)
                        *pexel=*pexel-2;
                    if (sco[fr]<110 && (*pexel)%2 && sc<1800) (*pexel)--;
                }
                if (*pexfl<2 && *pexel<2 && lvl>=mxvt[plv]) mxlv=lvl;
                if (!(*pexfl) && !(*pexel) && lvl>=plv) mxlv=lvl;
                if (lvl<=plv) mxlv=plv;
                if ((*pexfl>1 || *pexel>1) && lvl==mxlv && lvl<mxvf[plv]) mxlv++;
                if (((*pexfl)%2 || (*pexel)%2) && lvl==mxlv && lvl<mxvt[plv]) mxlv++;
            } // end vct
            else mxlv=plv;
            if (mxlv>ciel[lvl][cp])
                mxlv=ciel[lvl][cp];
            
            hfl=0;
            if (lvl<mxlv && *pmv<*pmxmv) {
                
                bd[x][y]=fr; // make move
                nstk[lvl]=0;
                for (xx=x-extnt; xx<x+1+extnt; xx++)
                    for (yy=y-extnt; yy<y+1+extnt; yy++)
                        if (xx>=0 && xx<19 && yy>=0 && yy<19)
                            if (!bd[xx][yy]) {
                                bd[xx][yy]=-1;
                                exstkx[lvl][nstk[lvl]]=xx;
                                exstky[lvl][nstk[lvl]++]=yy;
                            }
                
#if HASH == 1
                HValX[lvl] ^= (bd[x][y]*TableX[x+y*19]);
                HValY[lvl] ^= (bd[x][y]*TableY[x+y*19]);
#endif

                // chk poof — see dmov() for the credit convention (GameClass
                // per-form +1 bonus, on top of +1 per stone removed) and the
                // collect-then-apply rationale. Poof pushes share the generic
                // capture undo stack; each collected neighbor is one push
                // (capv=fr, restored as an own stone).
                ncap[lvl]=0;
                if (cfg.poofPairs) {
                    int qx[17], qy[17], qn=0, pairFired=0, tripleFired=0;
                    auto qpush = [&](int px, int py) {
                        if (qn>=17) return;
                        for (int qi=0; qi<qn; qi++) if (qx[qi]==px && qy[qi]==py) return;
                        qx[qn]=px; qy[qn]=py; qn++;
                    };
                    for (d=0; d<8; d++) {                        // pair form
                        c1=x+dx[d];  c2=y+dy[d];
                        c3=c1+dx[d]; c4=c2+dy[d];
                        c5=x-dx[d];  c6=y-dy[d];
                        if (c3>=0 && c3<19 && c4>=0 && c4<19 &&
                            c5>=0 && c5<19 && c6>=0 && c6<19)
                            if (bd[c1][c2]==fr &&
                                bd[c3][c4]>0 && bd[c3][c4]!=fr &&
                                bd[c5][c6]>0 && bd[c5][c6]!=fr) {
                                qpush(c1, c2);
                                pairFired=1;
                            }
                    }
                    if (cfg.poofTriples) {
                        for (d=0; d<8; d++) {                    // end form: own at +1d,+2d
                            c1=x+dx[d];    c2=y+dy[d];
                            c3=x+2*dx[d];  c4=y+2*dy[d];
                            c5=x+3*dx[d];  c6=y+3*dy[d];
                            c7=x-dx[d];    c8=y-dy[d];
                            if (c5>=0 && c5<19 && c6>=0 && c6<19 &&
                                c7>=0 && c7<19 && c8>=0 && c8<19)
                                if (bd[c1][c2]==fr && bd[c3][c4]==fr &&
                                    bd[c5][c6]>0 && bd[c5][c6]!=fr &&
                                    bd[c7][c8]>0 && bd[c7][c8]!=fr) {
                                    qpush(c1, c2); qpush(c3, c4);
                                    tripleFired=1;
                                }
                        }
                        for (d=0; d<4; d++) {                    // center form: own at +1a,-1a
                            c1=x+dx[d];    c2=y+dy[d];
                            c3=x-dx[d];    c4=y-dy[d];
                            c5=x+2*dx[d];  c6=y+2*dy[d];
                            c7=x-2*dx[d];  c8=y-2*dy[d];
                            if (c5>=0 && c5<19 && c6>=0 && c6<19 &&
                                c7>=0 && c7<19 && c8>=0 && c8<19)
                                if (bd[c1][c2]==fr && bd[c3][c4]==fr &&
                                    bd[c5][c6]>0 && bd[c5][c6]!=fr &&
                                    bd[c7][c8]>0 && bd[c7][c8]!=fr) {
                                    qpush(c1, c2); qpush(c3, c4);
                                    tripleFired=1;
                                }
                        }
                    }
                    if (qn) {
                        for (int qi=0; qi<qn; qi++) {
                            capx[lvl][ncap[lvl]]=qx[qi];
                            capy[lvl][ncap[lvl]]=qy[qi];
                            capv[lvl][ncap[lvl]++]=fr;   // restore as OWN stone
                            bd[qx[qi]][qy[qi]]=-1;
#if HASH == 1
                            HValX[lvl] ^= (fr*TableX[qx[qi]+19*qy[qi]]);
                            HValY[lvl] ^= (fr*TableY[qx[qi]+19*qy[qi]]);
#endif
                        }
                        bd[x][y]=-1;   // restore paths blindly reset played cell to -1
#if HASH == 1
                        HValX[lvl] ^= (fr*TableX[x+19*y]);   // reverse the placement XOR
                        HValY[lvl] ^= (fr*TableY[x+19*y]);
#endif
                        cc[lvl][3-fr]+=qn+pairFired+tripleFired;   // removed stones + per-form bonus
                    }
                }

                // chk capture
                if (cfg.capturePairs) {
                for (d=0; d<8; d++) {
                    c1=x+dx[d];
                    c2=y+dy[d];
                    c3=c1+dx[d];
                    c4=c2+dy[d];
                    c5=c3+dx[d];
                    c6=c4+dy[d];
                    c7=c5+dx[d];
                    c8=c6+dy[d];
                    if (c5>=0 && c5<19 && c6>=0 && c6<19)
                        if (bd[c1][c2]>0 && bd[c3][c4]>0 &&
                            bd[c1][c2]!=fr && bd[c3][c4]!=fr) {
                            if (bd[c5][c6]==fr) {
                                cc[lvl][fr]+=2;
                                capx[lvl][ncap[lvl]]=c1;
                                capy[lvl][ncap[lvl]]=c2;
                                capv[lvl][ncap[lvl]++]=bd[c1][c2];
                                capx[lvl][ncap[lvl]]=c3;
                                capy[lvl][ncap[lvl]]=c4;
                                capv[lvl][ncap[lvl]++]=bd[c3][c4];
                                bd[c1][c2]=-1;
                                bd[c3][c4]=-1;
                                
#if HASH == 1
                                HValX[lvl] ^= (en*TableX[c1+19*c2]);
                                HValY[lvl] ^= (en*TableY[c1+19*c2]);
                                HValX[lvl] ^= (en*TableX[c3+19*c4]);
                                HValY[lvl] ^= (en*TableY[c3+19*c4]);
#endif
                                
                            }
                            else {
                                if (c7>=0 && c7<19 && c8>=0 && c8<19 && cfg.captureTriples)
                                    if (bd[c7][c8]==fr && bd[c5][c6]>0) {
                                        cc[lvl][fr]+=3;
                                        capx[lvl][ncap[lvl]]=c1;
                                        capy[lvl][ncap[lvl]]=c2;
                                        capv[lvl][ncap[lvl]++]=bd[c1][c2];
                                        capx[lvl][ncap[lvl]]=c3;
                                        capy[lvl][ncap[lvl]]=c4;
                                        capv[lvl][ncap[lvl]++]=bd[c3][c4];
                                        capx[lvl][ncap[lvl]]=c5;
                                        capy[lvl][ncap[lvl]]=c6; 
                                        capv[lvl][ncap[lvl]++]=bd[c5][c6];
                                        bd[c1][c2]=-1;
                                        bd[c3][c4]=-1;
                                        bd[c5][c6]=-1;
                                        
#if HASH == 1
                                        HValX[lvl] ^= (en*TableX[c1+19*c2]);
                                        HValY[lvl] ^= (en*TableY[c1+19*c2]);
                                        HValX[lvl] ^= (en*TableX[c3+19*c4]);
                                        HValY[lvl] ^= (en*TableY[c3+19*c4]);
                                        HValX[lvl] ^= (en*TableX[c5+19*c6]);
                                        HValY[lvl] ^= (en*TableY[c5+19*c6]);
#endif
                                    }
                            }
                        } // if en*2
                } // next d
                } // if capturePairs

                // Boat: record the maximal own run this move completes, so the
                // survived check two plies on can tell whether the opponent
                // broke it. bd[x][y]==fr is required (a poof of the played
                // stone in O-Pente leaves it -1) so no phantom five is stored.
                pendN[lvl]=0;
                if (cfg.boatWin && bd[x][y]==fr)
                    pendN[lvl]=boatRun(x, y, fr, pendC[lvl]);

#if HASH == 1
                // Boat: fold in the pending-run state so a survived-win score
                // computed on one path (pendN set) can never be probed/reused
                // on a permuted path where the five is only just-completed
                // (provisional, not yet won). Order: pendN[lvl] record (just
                // above) -> pend-bit fold (here) -> hash probe (below), so the
                // bits reflect this node's just-written pendN[lvl] and the
                // still-valid pendN[lvl-1] from the parent ply. Local to the
                // probe key only (pfold is never folded into HValX/HValY[lvl]
                // themselves), so children -- which copy HValX[lvl]/HValY[lvl]
                // as their base -- never double-accumulate it. pendN[lvl-2] is
                // already consumed by the survived check below, so it is not
                // re-folded here.
                pb1 = (cfg.boatWin && pendN[lvl-1]>0) ? 1 : 0;
                pb2 = (cfg.boatWin && pendN[lvl]>0) ? 1 : 0;
                pfold = PENDBIT1*pb1 + PENDBIT2*pb2;
                htempx=(HValX[lvl] ^(TableX[361]*(cc[lvl][1]+cc[lvl][2])) ^ pfold)%1000000;
                htempy= HValY[lvl] ^(TableY[361]*(cc[lvl][1]+cc[lvl][2])) ^ pfold;
                if (*(pHashY+htempx)==htempy && np<3)
                    if (*(pHashD+htempx)==lvl) { //found in table!
                        sc=*(pHashS+htempx*2+fr-1);
                        scr[lvl][fr]=sc;
                        scr[lvl][3-fr]=-sc; 
                        if (sc>10000 || sc<-10000) wfl=1;
                        hfl=1;
                    }
#endif

                // Boat: if fr completed a five two plies ago (slot lvl-2) and it
                // still has >=5 consecutive live stones, the opponent's reply
                // failed to break it -> fr has already won, so every move here
                // is a win. fr at level lvl equals fr at lvl-2 (np==2), so the
                // recorded run's owner is exactly the current fr. Force the
                // standard win path (clear any hash hit so it takes effect).
                if (cfg.boatWin && np==2 && lvl>=2 && pendN[lvl-2]>0) {
                    int streak=0, best=0, c;
                    for (c=0; c<pendN[lvl-2]; c++)
                        if (bd[pendC[lvl-2][c].x][pendC[lvl-2][c].y]==fr) {
                            if (++streak>best) best=streak;
                        } else streak=0;
                    if (best>=5) { sc=12000; hfl=0; }
                }

#ifndef C6_NO_FORCE
                // Connect6 v3: bounded forcing-search win terminal (VCDF prover,
                // docs/connect6-v3-vcf-spec.md). Fires only on a turn-final ply
                // (c6NextSame==0), where BOTH of the mover's stones this turn are
                // on bd, so the prover models whole 2-stone turns from a settled
                // position. Gated to the no-capture two-stone variant (game 13) so
                // it is a strict no-op on every other path. tnRoot>=6 skips the
                // opening single-stone anomaly (refusal-only; v2's W1 base needs
                // >=15 mover stones so this changes no v2 verdict). Mirrors the
                // boat block above: set the 12000 band and let the existing win
                // path (below) do the rest. Never fires on a leaf ply (lvl==mxlv
                // skips this make-move block) or on a TT hit (!hfl). c6ForcedWin's
                // root base cases reproduce v2 c6UnstoppableThreat, so it stays a
                // strict superset of v2; c6ForceEnabled=false disables it. The
                // winRowLength==6 clause pins the prover's hardcoded six-in-a-row
                // geometry to the only variant that uses it (review F5).
                if (cfg.stonesPerTurn==2 && !cfg.capturePairs && cfg.winRowLength==6
                    && c6ForceEnabled && !hfl && c6NextSame==0 && tnRoot>=6 && lvl<=4
                    && c6ForcedWin(fr, lvl)) {
                    sc = 12000; hfl = 0;
                }
#endif

                if (sc>=10000 && !hfl) { //check win
                    
                    scr[lvl][fr]=12000-lvl;
                    scr[lvl][3-fr]=lvl-12000;
                    wfl=1;
                    if (lvl==1) {
                        scr[lvl][fr]=30000;
                        hmv[0]=mvlst[1][mv[1]];
                        wfl++;
                    }
                } // end win
                if (lvl==1 && *pmxmv==1) wfl=1; //forced move
            } // end non-maxlv
            fl1=1;
        } while (*pmv<*pmxmv && lvl<mxlv && !wfl && !hfl);
        fl1=0;
        if (*pmv>=*pmxmv) { //no more moves
            lvl--;
            // Backtrack must mirror the push path's fr assignment (see the
            // cfg.stonesPerTurn==2 branch above) for the level we are
            // returning to. A bare fr-- assumes strict alternation, but
            // Connect6's 1,2,2,1 rotation means popping back from the second
            // stone of a turn must NOT flip the player -- only recomputing
            // fr from tnRoot/lvl (exactly as the push path does) gets this
            // right for every rotation position.
            if (cfg.stonesPerTurn==2) { fr=ownerOf(tnRoot-2+lvl); c6NextSame=(ownerOf(tnRoot-1+lvl)==fr); }
            else { fr--; if (!fr) fr=np; }
        }
        else if (lvl==mxlv) {
            hmv[lvl]=mvlst[1][mv[1]];
            if (sc>10000) { // win
                scr[lvl][fr]=12000-lvl;
                scr[lvl][3-fr]=lvl-12000;
            } //end win
            else {
                for (i=1; i<=np; i++) sco[i]=0; //add up scores
                for (ii=1; ii<=lvl; ii++)
                    for (i=1; i<=np; i++) {
                        sco[i]+=mvsco[ii][mv[ii]][i];
                        if (sco[i]>7800) sco[i]=7800;
                    }
                en=3-fr;
                scr[lvl][fr]=sco[fr]-sco[en]*4+rand()%6;
                scr[lvl][en]=-scr[lvl][fr];
                for (ii=1; ii<=np; ii++) {
                    if (scr[lvl][ii]>10000) scr[lvl][ii]=10000;
                    if (scr[lvl][ii]<-10000) scr[lvl][ii]=-10000;
                }
            } // end else sco[fr]>10000
        } // end if maxlv
        
        if (wfl) {
            hmv[lvl]=mvlst[1][mv[1]];
            //mv[lvl]=mxmv[lvl];
        }
        
        if (lvl>0) {
            if (scr[lvl][fr] > scr[lvl-1][fr] 
                && (lvl>1 || scr[lvl][fr]<maxscr)) {
                if (12000-scr[lvl][fr]<ciel[lvl][cp])
                    ciel[lvl][cp]=12000-scr[lvl][fr];
                for (i=1; i<=np; i++)
                    scr[lvl-1][i]=scr[lvl][i];
                hmv[lvl-1]=hmv[lvl];
#if HASH == 1
                if (lvl<mxlv) {
                    // Same pend-bit fold as the probe above (Finding 1) --
                    // must match exactly or a stored key would never be
                    // found again by the probe for the same node.
                    pb1 = (cfg.boatWin && pendN[lvl-1]>0) ? 1 : 0;
                    pb2 = (cfg.boatWin && pendN[lvl]>0) ? 1 : 0;
                    pfold = PENDBIT1*pb1 + PENDBIT2*pb2;
                    htempx=(HValX[lvl] ^ (TableX[361]*
                                          (cc[lvl][1]+cc[lvl][2])) ^ pfold)%1000000;
                    htempy= HValY[lvl] ^(TableY[361]*(cc[lvl][1]+cc[lvl][2])) ^ pfold;
                    *(pHashY+htempx)=htempy;
                    
                    if (scr[lvl][fr]==beta) //|| sc==alpha
                        *(pHashD+htempx)=128+lvl;
                    else *(pHashD+htempx)=lvl;
                    for (i=1; i<3; i++) 
                        *(pHashS+htempx*2+i-1)=(short) scr[lvl][i];
                }
#endif
                
            }
            frmo=fr-1;
            if (frmo<1) frmo+=np;
            // Connect6 v1: DISABLE the lvl-2 sibling/parity cutoff. It assumes
            // ply lvl-2 is the same player as ply lvl (strict period-2
            // alternation), which is false for Connect6's period-4 rotation
            // (1,2,2,1,...) -- pruning on scr[lvl-2] would be unsound and could
            // drop a forced win/loss. Correctness first; a same-player-ancestor
            // rework is a later optimization (spec non-goal). The move is still
            // undone by the general backup block below, so search stays correct.
            if (lvl > 1 && cfg.stonesPerTurn!=2)
                if (scr[lvl][frmo] <= scr[lvl-2][frmo]) {
                    if (lvl<mxlv) {
                        rxy=mvlst[lvl][mv[lvl]];
                        ry=rxy/19;
                        rx=rxy%19;
                        bd[rx][ry]=-1;
                        for (ii=0; ii<ncap[lvl]; ii++) {
                            cpx=capx[lvl][ii];
                            cpy=capy[lvl][ii];
                            bd[cpx][cpy]=capv[lvl][ii];
                        }
                        for (ii=0; ii<nstk[lvl]; ii++)
                            bd[exstkx[lvl][ii]][exstky[lvl][ii]]=0;
                    }
                    lvl--;
                }
            if (np==2 && lvl==1 && scr[0][cp]<12000 && 12000-scr[0][cp]<ciel[0][cp])
                ciel[0][cp]=12000-scr[0][cp];
        } //lvl >0
        
        if (lvl>0 && lvl<mxlv) {
            rxy=mvlst[lvl][mv[lvl]];
            ry=rxy/19;
            rx=rxy%19;
            bd[rx][ry]=-1;
            for (ii=0; ii<ncap[lvl]; ii++) {
                cpx=capx[lvl][ii];
                cpy=capy[lvl][ii];
                bd[cpx][cpy]=capv[lvl][ii];
            }
            for (ii=0; ii<nstk[lvl]; ii++)
                bd[exstkx[lvl][ii]][exstky[lvl][ii]]=0;
        }
        lvl--;

        // Mobile stop seam: honored at this coarse per-node boundary exactly
        // where the OLD Android engine checked `stopped` (its Tree() did the same
        // wfl=2 bail right after lvl--). wfl=2 drops out of the outer do/while;
        // the `if (wfl==2 && lvl>0)` cleanup below unwinds the board and bmove is
        // returned as the current best legal move. stopfl stays 0 for WASM (no
        // requestStop caller) so this is a no-op there.
        if (stopfl) wfl=2;

        /*
         if (pDoc->Esc) {
         //check for ESC key
         pDoc->Esc=FALSE;
         wfl=2;
         ii=cp+1;
         if (ii>np) ii=1;
         pDoc->player[ii]=0;
         } */
        
        //time (&t2); //update time
        //sec[0]=t2-ti;
        
    } while (lvl>=0 && wfl<2);
    
    if (wfl==2 && lvl>0) {
        if (lvl==mxlv) lvl--;
        for (i=lvl; i>0; i--) {
            rxy=mvlst[i][mv[i]];
            ry=rxy/19;
            rx=rxy%19;
            bd[rx][ry]=-1;
            for (ii=0; ii<ncap[i]; ii++) {
                cpx=capx[i][ii];
                cpy=capy[i][ii];
                bd[cpx][cpy]=capv[i][ii];
            }
            for (ii=0; ii<nstk[i]; ii++)
                bd[exstkx[i][ii]][exstky[i][ii]]=0;
        }
    }
    
    bmove=hmv[0];
    
    if (!bmove) bmove=mvlst[1][0];
    bscr=scr[0][cp];
    if (mxmv[1]==1) bscr=0;
    
    return 0;
} 

int CAi::Eval(int x, int y) {
    CPoint pt;
    int s0, i, s[7], tcap2, tcap3;
    int x9, y9, bl, tfr, tcap1;

    // --- mobile portability hook seam (opt-in; wholly skipped when no listener,
    // so the WASM/default search allocates nothing and behaves identically) ---
    // Fires at the top of Eval(), matching the OLD Android engine's two JNI
    // callbacks: aiEvaluated() UNCONDITIONALLY per Eval, and (mask-gated)
    // aiVisualization() with a flattened 19x19 board where the cell under
    // evaluation is marked 3 (old: temp[x*size+y]=3, i outer / j inner).
    if (listener) {
        listener->aiEvaluated();
        if (callbackMask) {
            int vis[361], vk = 0;
            for (int vi = 0; vi < size; vi++)
                for (int vj = 0; vj < size; vj++)
                    vis[vk++] = bd[vi][vj];
            vis[x*size + y] = 3;
            listener->aiVisualization(vis, 361);
        }
    }

    lvl--;
    for (i=1; i<7; i++) s[i]=0;
    gf=0;
    pt.x=x;
    pt.y=y;
    s0=Score(pt); //s0=0
    
    if (s0>10000) {
        for (i=1; i<=np; i++) sco[i]=-12000;
        sco[fr]=12000;
    }
    else {
        for (i=1; i<=np; i++) s[i]=sco[i];
        gf=1;
        x9=x; y9=y;
        tfr=fr;
        tcap1=cap1;
        int tcapP=capP; //poofed own stones (initial Score set capP; nested Score calls clobber it)
        for (bl=0; bl<tcap1; bl++ ) { //score captured
            x=p1xy[bl].x; y=p1xy[bl].y;
            ppd=p1d[bl];
            if (ppd>4) ppd=ppd-4;
            fr=3-tfr;
            pt.x=x;
            pt.y=y;
            s0=Score(pt);
            sco[1]=sco[1]-sco[1]/10;
            sco[2]=sco[2]-sco[2]/10;
            s[1]-=sco[1];
            s[2]-=sco[2];
            
        } // next bl
        if (np==2) tcap2=cap2;
        else tcap2=0;
        for (bl=0; bl<tcap2; bl++ ) { //threatened
            x=p2xy[bl].x; y=p2xy[bl].y;
            ppd=p2d[bl];
            if (ppd>4) ppd=ppd-4;
            fr=3-tfr;
            pt.x=x;
            pt.y=y;
            s0=Score(pt);
            s[1]-=sco[1]/8;
            s[2]-=sco[2]/8;
        } // next bl
        tcap3=cap3;
        for (bl=0; bl<tcap3; bl++ ) { //protected
            x=p3xy[bl].x; y=p3xy[bl].y;
            ppd=p3d[bl];
            if (ppd>4) ppd=ppd-4;
            fr=tfr;
            pt.x=x;
            pt.y=y;
            s0=Score(pt);
            s[1]+=sco[1]/8;
            s[2]+=sco[2]/8;
        } // next bl
        int tcapP2=tcapP;
        for (bl=0; bl<tcapP2; bl++ ) { //poofed - own stones vanish
            x=pPxy[bl].x; y=pPxy[bl].y;
            ppd=pPd[bl];
            if (ppd>4) ppd=ppd-4;
            fr=tfr;
            pt.x=x;
            pt.y=y;
            s0=Score(pt);
            sco[1]=sco[1]-sco[1]/10;
            sco[2]=sco[2]-sco[2]/10;
            s[1]-=sco[1];
            s[2]-=sco[2];
        } // next bl
        fr=tfr;
        x=x9; y=y9;
        
        for (i=1; i<=np; i++) {
            sco[i]=s[i];
            if (sco[i]>7800) sco[i]=7800;
        }
        s0=sco[fr]-sco[3-fr]*4;
        
        if (s0>9500) s0=9500;
    } //end else
    
    gf=0;
    
    lvl++;
    return s0;
}

int CAi::Score(CPoint pt) {
    int dv, cx, cy, iw, qs, po, tys, hlim, g1, s0;
    int i, x, y, f0, side, sign, index, c4, c5;
    int f1[2], sp[2], la[5], lb[9], c2[7], c3[7], g[2], lc[5], ld[9];
    int item, fl, lx, ly, df, iv, lf[3];
    
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
    //9 ?OOX ##############
    
    x=pt.x;
    y=pt.y;
    cap1=cap2=cap3=0;
    capP=0;
    capPf=0;
    rowWin=0;
    c4=c5=0;
    dv=0;
    for (i=1; i<7; i++) sco[i]=c3[i]=0;

    // Connect6: direct 6-window eval, bypassing the 5-based pattern tables. The
    // counters above are already zeroed and Score6 leaves them 0, so Eval()'s
    // capture/threatened/protected/poof loops all skip (list-empty-safe).
    if (cfg.winRowLength == 6) return Score6(pt);

    hlim=4;
    if (cfg.captureTriples) hlim=5;

    if (cfg.poofPairs && !gf) {  // poof scan: own neighbors sandwiched by enemies vanish
        // collect a poofed neighbor (deduped, axis-tagged for Eval's rescore loop)
        auto pPush = [&](int px, int py, int ax) {
            if (capP>=17) return;
            for (int qi=0; qi<capP; qi++) if (pPxy[qi].x==px && pPxy[qi].y==py) return;
            pPxy[capP].x=px; pPxy[capP].y=py; pPd[capP]=ax; capP++;
        };
        int pf=0, tf=0;   // per-form referee bonus flags (see dmov()'s pairFired/tripleFired)
        for (i=0; i<8; i++) {                            // pair form
            int n1x=x+dx[i],   n1y=y+dy[i];              //   own neighbor (+1d)
            int f1x=x+2*dx[i], f1y=y+2*dy[i];            //   far enemy    (+2d)
            int f2x=x-dx[i],   f2y=y-dy[i];              //   near enemy   (-1d)
            if (f1x>=0 && f1x<19 && f1y>=0 && f1y<19 &&
                f2x>=0 && f2x<19 && f2y>=0 && f2y<19)
                if (bd[n1x][n1y]==fr &&
                    bd[f1x][f1y]>0 && bd[f1x][f1y]!=fr &&
                    bd[f2x][f2y]>0 && bd[f2x][f2y]!=fr) {
                    pPush(n1x, n1y, i%4);
                    pf=1;
                }
        }
        if (cfg.poofTriples) {
            for (i=0; i<8; i++) {                        // end form: own at +1d,+2d
                int e1x=x+dx[i],   e1y=y+dy[i];
                int e2x=x+2*dx[i], e2y=y+2*dy[i];
                int e3x=x+3*dx[i], e3y=y+3*dy[i];        //   far enemy (+3d)
                int e4x=x-dx[i],   e4y=y-dy[i];          //   near enemy(-1d)
                if (e3x>=0 && e3x<19 && e3y>=0 && e3y<19 &&
                    e4x>=0 && e4x<19 && e4y>=0 && e4y<19)
                    if (bd[e1x][e1y]==fr && bd[e2x][e2y]==fr &&
                        bd[e3x][e3y]>0 && bd[e3x][e3y]!=fr &&
                        bd[e4x][e4y]>0 && bd[e4x][e4y]!=fr) {
                        pPush(e1x, e1y, i%4); pPush(e2x, e2y, i%4);
                        tf=1;
                    }
            }
            for (i=0; i<4; i++) {                        // center form: own at +1a,-1a
                int e1x=x+dx[i],   e1y=y+dy[i];
                int e2x=x-dx[i],   e2y=y-dy[i];
                int e3x=x+2*dx[i], e3y=y+2*dy[i];        //   enemy (+2a)
                int e4x=x-2*dx[i], e4y=y-2*dy[i];        //   enemy (-2a)
                if (e3x>=0 && e3x<19 && e3y>=0 && e3y<19 &&
                    e4x>=0 && e4x<19 && e4y>=0 && e4y<19)
                    if (bd[e1x][e1y]==fr && bd[e2x][e2y]==fr &&
                        bd[e3x][e3y]>0 && bd[e3x][e3y]!=fr &&
                        bd[e4x][e4y]>0 && bd[e4x][e4y]!=fr) {
                        pPush(e1x, e1y, i%4); pPush(e2x, e2y, i%4);
                        tf=1;
                    }
            }
        }
        capPf=pf+tf;   // 0-2: consumed below, in this same Score() call's tail
    }

    do { //c0
        if (gf==1 && dv==ppd) { //just need c4 - eval captured stone
            for (sign=-1; sign<2; sign+=2) { //look for captures
                for (iw=1; iw<hlim; iw++) {
                    lc[iw]=0;
                    cx=x+dx[dv]*iw*sign;
                    cy=y+dy[dv]*iw*sign;
                    if (cx>=0 && cx<19 && cy>=0 && cy<19) {
                        qs=bd[cx][cy];
                        if (qs>0) la[iw]=qs;
                        else la[iw]=0;
                    } else la[iw]=-1;
                } //iw
                if (la[1]>0 && la[1]!=fr)
                    if (la[2]>0 && la[2]!=fr) { //b0
                        if (la[3]==fr) c4+=2; //pair now open for capture
                        if (!la[3]) c5+=2; //pair no longer open for capture
                        if (la[3]>0 && la[3]!=fr && cfg.captureTriples) {
                            if (la[4]==fr) c4+=3;
                            if (!la[4]) c5+=3;
                        }
                    } // b0
            } //sign
            dv++;
        } //gf/dv
        
        if (dv<4) {
            for (sign=-1; sign<2; sign+=2) { //look for captures
                lf[sign+1]=-1;
                for (iw=1; iw<hlim; iw++) {
                    lc[iw]=0;
                    cx=x+dx[dv]*iw*sign; //first
                    cy=y+dy[dv]*iw*sign;
                    if (cx>=0 && cx<19 && cy>=0 && cy<19) {
                        qs=bd[cx][cy];
                        if (qs>0) {
                            la[iw]=qs;
                            if (lf[sign+1]<0) lf[sign+1]=qs;
                        } else la[iw]=0;
                    } else la[iw]=-1;
                } //iw
                g1=0;
                if (!la[1]) g1=8;
                if (la[1]==fr) {
                    if (!la[2]) g1=6;
                    if (la[2]==fr && !la[3]) g1=7;
                    if (la[2]==fr && np==2 && !gf) { //protected pair
                        if (la[3]>0 && la[3]!=fr && !cfg.captureTriples) { //g1=9
                            for (i=1; i<3; i++) {
                                p3xy[cap3].x=x+dx[dv]*sign*i;
                                p3xy[cap3].y=y+dy[dv]*sign*i;
                                p3d[cap3]=dv;
                                cap3++;
                            }
                        }
                    }//prot pr
                }
                if (la[1]>0 && la[1]!=fr) { //en
                    if (!la[2]) g1=1;
                    if (la[2]>0 && la[2]!=fr) { //b0
                        if (!la[3]) { //threaten
                            g1=2;
                            if (!gf) for (i=1; i<3; i++) { //threatened pair
                                p2xy[cap2].x=x+dx[dv]*sign*i;
                                p2xy[cap2].y=y+dy[dv]*sign*i;
                                p2d[cap2]=dv;
                                cap2++;
                            }
                        }
                        
                        if (la[3]==fr && !gf) {
                            g1=3;
                            for (i=1; i<3; i++) {
                                p1xy[cap1].x=x+dx[dv]*sign*i;
                                p1xy[cap1].y=y+dy[dv]*sign*i;
                                p1d[cap1]=dv;
                                cap1++;
                                lc[i]=la[i];
                                la[i]=0;
                            }
                        }
                        if (la[3]==fr && gf==1) {
                            g1=0;
                            c4+=2;
                        }
                        if (la[3]>0 && la[3]!=fr && cfg.captureTriples) {
                            if (la[4]==fr && !gf) {
                                g1=5;
                                for (i=1; i<4; i++) {
                                    p1xy[cap1].x=x+dx[dv]*i*sign;
                                    p1xy[cap1].y=y+dy[dv]*i*sign;
                                    p1d[cap1]=dv;
                                    cap1++;
                                    lc[i]=la[i];
                                    la[i]=0;
                                }
                            }
                            if (la[4]==fr && gf==1) {
                                g1=0;
                                c4+=3;
                            }
                            if (!la[4]) g1=4;
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
            for (i=1; i<=np; i++) c2[i]=0;
            sp[0]=sp[1]=0;
            if (g[0]==8 || g[0]==3 || g[0]==5) sp[0]=1;
            if (g[1]==8 || g[1]==3 || g[1]==5) sp[1]=1;
            for (i=0; i<2; i++) {
                if (g[i]==6 && sp[1-i]) { // pairs
                    if (cfg.captureTriples) sco[fr]-=20;
                    else sco[fr]-=12;
                }
                if (cfg.captureTriples && g[i]==7 && sp[1-i]) sco[fr]-=12;
                if (!cfg.captureTriples && g[i]==7) sco[fr]+=12;
                if (g[i]==2) { // threaten a pair
                    sco[fr]+=50;
                    if (cc[lvl][fr]+cap1>=cfg.capWinCount-2) sco[fr]+=1024;
                }
                if (cfg.captureTriples && g[i]==4) {
                    sco[fr]+=75;
                    if (cc[lvl][fr]+cap1>=cfg.capWinCount-3) sco[fr]+=1024;
                }
            } // next i
            if (cfg.captureTriples && g[0]==6 && g[1]==6) sco[fr]-=12; //pair
            for (iw=0; iw<5; iw+=4) { // O = played
                for (i=1; i<=np; i++) { // Z = potential capturer
                    if (i!=fr && lb[iw+1]>0 && lb[iw+2]>0) // OXYZ
                        if (lb[iw+1]!=i && lb[iw+2]!=i) { // protect
                            if (lb[iw+3]==i && (!cfg.captureTriples || lb[5-iw])) c3[i]+=2;
                            if (cfg.captureTriples && lb[iw+3]!=i && lb[iw+3]>0 && lb[iw+4]==i) c3[i]+=3;
                        }
                } // next i
                i=lb[5-iw];
                if (i>0 && i!=fr)
                    if (lb[iw+1]>0 && lb[iw+1]!=i) { // _XOZ
                        if (!lb[iw+2]) c2[i]+=2; // make suscept. pair
                        if (cfg.captureTriples && lb[iw+2]>0 && lb[iw+2]!=i && !lb[iw+3]) c2[i]+=3;
                    }
                i=lb[6-iw];
                if (i>0 && i!=fr)
                    if (lb[5-iw]>0 && lb[5-iw]!=i) { // _OXZ
                        if (!lb[iw+1]) c2[i]+=2;
                        if (cfg.captureTriples && lb[iw+1]>0 && lb[iw+1]!=i && !lb[iw+2]) c2[i]+=3;
                    }
                i=lb[7-iw];
                if (i>0 && i!=fr && cfg.captureTriples) // _OXYZ
                    if (lb[6-iw]>0 && lb[6-iw]!=i && lb[5-iw]>0 && lb[5-iw]!=i)
                        if (!lb[iw+1]) c2[i]+=3;
            } // next iw
            for (i=1; i<=np; i++) {
                if (i!=fr) { // c2 is stones now able to take
                    s0=c2[i]*25;
                    if (cc[lvl][i]+c2[i]>=cfg.capWinCount) s0=s0+2048;
                    sco[i]+=s0;
                }
            } // next i
              // end capt
            
            po=0;
            f0=-1;
            
            do { //c1 //look up in table
                f1[0]=f1[1]=-1;
                index=0;
                fl=sign=1;
                
                if (lf[0]!=fr && lf[2]!=fr) //eval pre-capt
                    for (i=1; i<hlim+3; i++)
                        if (!lb[i] && ld[i] && i!=4) lb[i]=ld[i];
                do { //c2
                	// printf("kitty %d\n", index);
                    iw=*(pAt+index*4+0)*sign;
                    side=1; iv=iw+4;
                    if (iw<0) { side=0; iv=-iw; }
                    qs=-2;
                    if (iw>-hlim && iw<hlim) qs=lb[iv];
                    if (qs<-1) {
                        cx=x+dx[dv]*iw;
                        cy=y+dy[dv]*iw;
                        if (cx>=0 && cx<19 && cy>=0 && cy<19) {
                            qs=bd[cx][cy];
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
                    index=*(pAt+index*4+item);
                    if (index<0) {
                        index=-index;
                        sign=-sign;
                    }
                    if (index>9999) fl=0;
                } while (fl); //c2
                index-=10000;
                tys=*(pAs+index*14+5); //white pattern
                if (f0==fr) *(pFk+(x+y*19)*4 +dv)=tys;
                
                //score for friend / enemy
                if (f0==fr) {
                    if (np<3 || multipbem) df=*(pAs+index*14+2);
                    else df=*(pAs+index*14+0);
                    sco[fr]+=df;
                    
                    if (!gf) {
                        
                        if (tys==2 || tys==3)
                            for (i=0; i<*(pAs+index*14+7); i++) {
                                pfhn=pFh+fhn*4;	 
                                *(pfhn+2)=tys;
                                lx=x+dx[dv]*(*(pAs+index*14+8+i))*sign;
                                ly=y+dy[dv]*(*(pAs+index*14+8+i))*sign;
                                *(pfhn+1)=lx+ly*19;
                                *(pfhn+0)=mvct;
                                *(pfhn+3)=dv;
                                fhn++;
                                if (fhn>749) {
                                    fhn=749;//errmsg(0);
                                    ferr++;
                                }
                            }
                        if (tys==4 || tys==11 || tys==12)
                            for (i=0; i<*(pAs+index*14+7); i++) {
                                pfhn=pFh+(fhn)*4;
                                if ((tys==4 && i<2) || (tys==11 && i>1) || (tys==12 && i<2))
                                    *(pfhn+2)=3;
                                else *(pfhn+2)=2;
                                lx=x+dx[dv]*(*(pAs+index*14+8+i))*sign;
                                ly=y+dy[dv]*(*(pAs+index*14+8+i))*sign;
                                *(pfhn+1)=lx+ly*19;
                                *(pfhn+0)=mvct;
                                *(pfhn+3)=dv;
                                fhn++;
                                if (fhn>749) {
                                    fhn=749; //errmsg(0);
                                    ferr++;
                                }
                            }
                    } //np=2, !gf
                } //friend
                else {
                    // f0 can be -1 here (never assigned away from its -1 init
                    // when qs never triggers the f0==-1 branch above), which
                    // would alias the erfl member via sco[-1] -- pre-existing
                    // UB that just happens to hit a dead write (erfl is never
                    // read again after CAi::reset()/ctor init). Guard it so
                    // the write only ever lands inside sco[1..np].
                    if (f0>0) sco[f0]-=*(pAs+index*14+3);
                }
                if (f0==f1[0]) f0=f1[1]; //eval other player
                else f0=f1[0];
                
            } while (po==1); //c1
            
            dv++;
        } //!dv==4
        
    } while (dv<4 && sco[fr]<10000); // c0

    if (sco[fr]>=10000) rowWin=1; // boat: a genuine >=5 row win (not the tail's capture-win)

    for (i=1; i<=np; i++) {
        if (i!=fr) {
            s0=-c3[i]*25; // c3 is stones now blocked from capture
            if (cc[lvl][i]+c3[i]>=cfg.capWinCount) s0=s0-1024;
            sco[i]+=s0;
        }
    } // next i
    sco[fr]+=cap1*160; // captures
    s0=(c4-c5)*25; // c4 is stones now open for capture
    if (cc[lvl][fr]+c4>=cfg.capWinCount) s0=s0+1024;
    if (cc[lvl][fr]+c5>=cfg.capWinCount) s0=s0-1024;
    sco[fr]-=s0; //is subtracted in eval
    if (cfg.poofPairs && capP>0) {
        if (sco[fr]>=10000) sco[fr]=sco[fr]/8; // row "win" poofs away with the stone
        s0=(capP+capPf)*160;                       // material handed to the opponent (+1 per fired form)
        if (cc[lvl][3-fr]+capP+capPf>=cfg.capWinCount) s0+=11000; // poof gifts capture win
        sco[3-fr]+=s0;
    }
    // Boat: a completed five only wins outright when some >=5 run through the
    // played point has no pair-capturable stone; otherwise it is provisional --
    // strong but nonterminal. Runs AFTER the poof clamp so a five already poofed
    // away (O-Pente) is never re-promoted. Gated !gf so only the primary move
    // eval decides terminality, not the nested capture-rescore Score() calls.
    if (cfg.boatWin && !gf && rowWin && sco[fr]>=10000) {
        // 9000 is nonterminal by design (it is deliberately kept below the
        // >=10000 win threshold sco[fr] just failed above) -- and it carries
        // no special magnitude either: Eval()'s else-branch clamps sco[] to
        // 7800 before any consumer ever sees it, so this value only nudges
        // move ordering within that clamp. Win enforcement for a provisional
        // five is NOT carried by this score at all; it's carried by Tree()'s
        // pendN/pendC survived-check mechanism two plies later.
        if (!boatRunProof(x, y, fr)) sco[fr]=9000;
    }
    if (cc[lvl][fr]+cap1>=cfg.capWinCount
        && (!cfg.poofPairs || cc[lvl][fr]+cap1>cc[lvl][3-fr])) sco[fr]=12000;
    
    if (sco[fr]>12000) sco[fr]=12000;
    s0=sco[fr];

    return s0;
}

// ---- Connect6 direct 6-window evaluator ---------------------------------
// Called from Score() for cfg.winRowLength==6, replacing the 5-based pattern
// tables entirely. Score() has already zeroed cap1/cap2/cap3/capP/capPf/rowWin,
// and Score6 leaves them 0, so Eval()'s capture/threatened/protected/poof
// rescore loops all skip; Eval consumes only sco[] and this function's return.
//
// Contract mirrors Score(): sco[fr] holds the mover's value (>=0), sco[3-fr]
// holds the opponent's threat stored NEGATIVE, so Eval()'s sco[fr]-sco[3-fr]*4
// turns "opponent could complete here" into a large positive blocking value
// (no separate blocking mechanism -- the *4 defense line does it). Returns
// sco[fr]; a 12000 return trips Eval()'s s0>10000 win path.
//
// For each perspective P in {fr, 3-fr}, treat (x,y) as a hypothetical P stone
// and slide every length-6 window containing (x,y) along all 4 axes:
//   - window must be fully on-board with no enemy (non-P) stone, else skip;
//   - n = P stones in the window (1..6, including the hypothetical center);
//   - n==6 => a completed six => terminal, magnitude 12000. This also covers
//     overlines: any 7+ run contains a full 6-window, so n==6 fires there too;
//   - else add w6[n], w6={_,1,6,32,200,1600} for n=1..5.
// Magnitude is capped at 12000. Empty cells read 0 or -1; off-board is -2.
int CAi::Score6(CPoint pt) {
    int x=pt.x, y=pt.y;
    static const int w6[7] = {0, 1, 6, 32, 200, 1600, 0}; // index by n; n==6 terminal
    for (int sd=0; sd<2; sd++) {
        int P = (sd==0) ? fr : 3-fr;
        int acc = 0, terminal = 0;
        // Win-in-pair (Connect6, two stones per turn): a length-6 window holding
        // 5 mover stones (incl. the hypothetical stone at pt) + 1 on-board empty
        // is ALREADY a made win when the mover still has this turn's second stone
        // to play (c6NextSame): the opponent never moves between the pair, so the
        // mover just drops the last stone in that empty cell -> six in a row. So
        // for the MOVER perspective (sd==0) with a second stone coming, n==5 is
        // terminal, not the w6[5]=1600 nonterminal "extend a four" score.
        //
        // Only the mover gets this. For the OPPONENT perspective (sd==1) an n==5
        // window is the opponent's OWN win-in-pair -- but it is not our stone to
        // complete and it is not their move yet. Their win-in-pair is evaluated as
        // a real terminal when the search reaches THEIR ply (they become fr with
        // their own c6NextSame). Within our ply we must score it as the top
        // nonterminal threat (w6[5], flowing through the existing negative-mag
        // convention into Eval's sco[fr]-sco[3-fr]*4 defense line) so blocking it
        // stays urgent -- terminalizing it here would fake a loss that isn't forced.
        // c6NextSame==0 for the pair's SECOND stone (no third stone this turn), so
        // n==5 is correctly non-terminal there too.
        int winN = (sd==0 && c6NextSame) ? 5 : 6;
        for (int a=0; a<4 && !terminal; a++) {
            int line[11];                          // offsets -5..+5 along axis a
            for (int k=0; k<11; k++) {
                if (k==5) { line[k]=P; continue; } // center = hypothetical P stone
                int cx=x+(k-5)*dx[a], cy=y+(k-5)*dy[a];
                if (cx<0||cx>=19||cy<0||cy>=19) line[k]=-2;   // off-board
                else line[k]=bd[cx][cy];           // 1/2 = stone, 0 or -1 = empty
            }
            for (int i0=0; i0<=5 && !terminal; i0++) {   // 6 windows containing the center
                int off=0, blk=0, n=0;
                for (int j=i0; j<i0+6; j++) {
                    int v=line[j];
                    if (v==-2) { off=1; break; }            // window runs off-board
                    if (v>0 && v!=P) { blk=1; break; }      // enemy stone -> dead window
                    if (v==P) n++;                          // center + any P stones
                }
                if (off || blk) continue;
                if (n>=winN) { terminal=1; break; }   // n>=6 always; n==5 too when win-in-pair
                acc += w6[n];
            }
        }
        int mag = terminal ? 12000 : (acc>12000 ? 12000 : acc);
        if (sd==0) { sco[fr]=mag; if (terminal) rowWin=1; }  // mover: positive
        else       sco[3-fr] = -mag;                         // opponent: negative
    }
    return sco[fr];
}

// Connect6 v2 (game 13 only): returns true iff mover P, having just placed BOTH
// stones of its turn, has left on the board >= 3 length-6 "five" windows
// (exactly 5 P + 1 empty + 0 enemy) whose >= 3 DISTINCT completion cells the
// opponent's two blocking stones cannot all cover -- a proven forced six on P's
// next turn. Pure function of (bd, P, dx[0..3], dy[0..3]); no side effects.
//
// Correctness (fives-only covering argument):
//  (a) No captures in Connect6 (cfg.capturePairs==false), so P's 5 stones in a
//      counted window are permanent; the ONLY cell the opponent can occupy to
//      stop that window's six is its single empty cell. Each counted window's
//      cover set is therefore the singleton {emptyIdx}.
//  (b) The opponent places exactly 2 stones next turn -> it can occupy at most 2
//      DISTINCT cells. seen[] dedups completion cells across overlapping/cross-
//      axis windows, so nDistinct is exactly the number of independent single-
//      cell obligations the opponent faces.
//  (c) nDistinct>=3 => for ANY 2 cells the opponent takes, some counted window's
//      completion cell stays empty; P drops one stone there next turn -> six.
//  (d) oppImm veto: if the opponent can itself make six in one turn first
//      (a window with 0 P and >=4 O, i.e. <=2 empties it fills with its 2
//      stones), refuse -- the opponent wins the race on the intervening turn.
// Only error mode is a mistaken REFUSAL (safe false negative, falls back to v1
// search); no false positive can fire (see spec section 4). Fired only on
// turn-final plies (c6NextSame==0) where the whole pair is on the board.
// (v2 body relocated: c6UnstoppableThreat is now a c6ScanFives wrapper, defined
// just below so v2 and v3 share the one scan. The covering argument above is
// unchanged and is the W1 base case of the v3 prover.)

// ===== Connect6 v3 forcing-search prover (docs/connect6-v3-vcf-spec.md) ===
// One whole-board pass (same window geometry/counting as v2's audited loop),
// classifying every on-board 6-window through the hypothetical bd:
//   pSix  = a 6-window is all P (a made six; overlines contain one).
//   pFour = a 0-enemy window holds >=4 P => P can six THIS turn (canSixNow(P)).
//   oppImm= a 0-P window holds >=4 O => O can six in one 2-stone turn (v2 veto).
//   nDistinct/c1/c2 = count + first two of P's DISTINCT five(pc==5,oc==0)
//                     completion cells (the singleton cover cells; seen[] dedup).
C6Scan CAi::c6ScanFives(int P) {
    int O = 3 - P;
    C6Scan r; r.nDistinct = 0; r.pSix = r.pFour = r.oppImm = false;
    r.c1.x = r.c1.y = r.c2.x = r.c2.y = -1;
    bool seen[361]; for (int i = 0; i < 361; i++) seen[i] = false;
    for (int a = 0; a < 4; a++)
        for (int sx = 0; sx < 19; sx++)
            for (int sy = 0; sy < 19; sy++) {
                int ex5 = sx + 5*dx[a], ey5 = sy + 5*dy[a];
                if (ex5 < 0 || ex5 >= 19 || ey5 < 0 || ey5 >= 19) continue;
                int pc = 0, oc = 0, ex = -1, ey = -1;
                for (int k = 0; k < 6; k++) {
                    int cx = sx + k*dx[a], cy = sy + k*dy[a], v = bd[cx][cy];
                    if (v == P) pc++;
                    else if (v == O) oc++;
                    else { ex = cx; ey = cy; }        // last empty; for pc==5 (oc==0) it is the unique one
                }
                if (pc == 6) r.pSix = true;
                if (oc == 0 && pc >= 4) r.pFour = true;
                if (pc == 0 && oc >= 4) r.oppImm = true;
                if (oc == 0 && pc == 5) {             // a five: single completion at (ex,ey)
                    int idx = ey*19 + ex;
                    if (!seen[idx]) { seen[idx] = true;
                        if (r.nDistinct == 0) { r.c1.x = ex; r.c1.y = ey; }
                        else if (r.nDistinct == 1) { r.c2.x = ex; r.c2.y = ey; }
                        r.nDistinct++;
                    }
                }
            }
    return r;
}

// v2 covering terminal, now a thin wrapper over the shared scan. Behavior is
// byte-identical to the original loop (T0_fuzz gates this): !veto && |C|>=3.
bool CAi::c6UnstoppableThreat(int P) {
    C6Scan r = c6ScanFives(P);
    return !r.oppImm && r.nDistinct >= 3;
}

// OR-node candidate generator (structure theorem, spec 4.1-4.2). When the
// caller reaches generation, canSixNow(P) is false => no 0-enemy window holds
// >=4 P => a NEW five can only be made by placing BOTH of this turn's stones
// into a window currently holding exactly 3 P + 0 O (3 empties); filling any 2
// of its 3 empties makes that window a five. Emits those pairs (packed a*361+b,
// a<b), deduplicated, capped at max. Generation is refusal-only for soundness:
// the child c6And re-derives fives/|C|/veto from a fresh scan, so a missed or
// useless pair only costs a (safe) false negative -- never a false positive.
int CAi::c6GenOpenFivePairs(int P, int *out, int max) {
    int O = 3 - P, n = 0;
    for (int a = 0; a < 4 && n < max; a++)
        for (int sx = 0; sx < 19 && n < max; sx++)
            for (int sy = 0; sy < 19 && n < max; sy++) {
                int ex5 = sx + 5*dx[a], ey5 = sy + 5*dy[a];
                if (ex5 < 0 || ex5 >= 19 || ey5 < 0 || ey5 >= 19) continue;
                int pc = 0, oc = 0, ec[6], ne = 0;
                for (int k = 0; k < 6; k++) {
                    int cx = sx + k*dx[a], cy = sy + k*dy[a], v = bd[cx][cy];
                    if (v == P) pc++; else if (v == O) oc++; else ec[ne++] = cy*19 + cx;
                }
                if (oc != 0 || pc != 3) continue;    // need 3 P + 0 O + exactly 3 empties
                for (int i = 0; i < ne && n < max; i++)
                    for (int j = i+1; j < ne && n < max; j++) {
                        int lo = ec[i] < ec[j] ? ec[i] : ec[j];
                        int hi = ec[i] < ec[j] ? ec[j] : ec[i];
                        int packed = lo * 361 + hi;
                        bool dup = false; for (int q = 0; q < n; q++) if (out[q] == packed) { dup = true; break; }
                        if (!dup) out[n++] = packed;
                    }
            }
    return n;
}

// AND node: P just completed a turn (both stones on bd); O to move. Returns
// true only if P's win is PROVEN (else refuse -> safe). Recurses solely on the
// |C|==2 unique-forced-reply shape (Lemma L2): O's whole 2-stone turn is forced
// onto {c1,c2} or O loses to one P stone next turn.
bool CAi::c6And(int P, int d) {
    if (--c6Budget < 0) return false;                // budget exhausted: REFUSE
    C6Scan R = c6ScanFives(P);
    if (R.pSix)           return true;               // W0: P six already on board
    if (R.oppImm)         return false;              // O sixes on its intervening turn: REFUSE
    if (R.nDistinct >= 3) return true;               // W1: v2 covering base
    if (R.nDistinct != 2 || d == 0) return false;    // |C|<=1 => O has a free stone: REFUSE
    int s1 = bd[R.c1.x][R.c1.y], s2 = bd[R.c2.x][R.c2.y];   // save EXACT values (0 vs -1)
    bd[R.c1.x][R.c1.y] = 3-P;  bd[R.c2.x][R.c2.y] = 3-P;
    bool w = c6Or(P, d - 1);
    bd[R.c1.x][R.c1.y] = s1;   bd[R.c2.x][R.c2.y] = s2;     // exact restore
    return w;
}

// OR node: P to place 2 stones. Wins via canSixNow (B1) or any generated
// forcing pair that makes the child AND provable.
bool CAi::c6Or(int P, int d) {
    if (--c6Budget < 0) return false;
    C6Scan R = c6ScanFives(P);
    if (R.pSix)  return true;                        // defensive (six already present)
    if (R.pFour) return true;                        // B1: canSixNow(P) -> six this turn
    int pairs[C6_PAIR_MAX];
    int np = c6GenOpenFivePairs(P, pairs, C6_PAIR_MAX);
    for (int i = 0; i < np; i++) {
        int ca = pairs[i] / 361, cb = pairs[i] % 361;
        int ax = ca%19, ay = ca/19, bx = cb%19, by = cb/19;
        int va = bd[ax][ay], vb = bd[bx][by];        // both empty by construction
        bd[ax][ay] = P; bd[bx][by] = P;
        bool w = c6And(P, d);
        bd[ax][ay] = va; bd[bx][by] = vb;
        if (w) return true;
    }
    return false;                                    // no proving pair found: REFUSE
}

// Top level: replaces the v2 call at the injection site. v2 fast path first
// (a free W1@root), then the bounded AND-OR recursion under a fresh scan budget
// and a depth ladder (K attacking turns) chosen by injection ply.
bool CAi::c6ForcedWin(int P, int lvl) {
    // No separate v2 fast path: c6And's OWN root base cases (pSix / oppImm veto /
    // nDistinct>=3) reproduce c6UnstoppableThreat exactly before any recursion,
    // so calling it first would only re-scan the root board (review F4). v3 is
    // therefore still a strict superset of v2 (subsumption test gates this).
    c6Budget = C6_SCAN_BUDGET;
    int K = (lvl <= 2) ? 4 : 2;
    bool w = c6And(P, K);
    if (w) c6ForceHits++;                            // instrumentation: the terminal proved a win
    return w;
}

// ---- Boat-Pente provisional-five helpers --------------------------------
// Collect the maximal own run (owner p) on axis a (a=0..3 select the four line
// directions dx[a]/dy[a]) through (x,y), treating (x,y) itself as a p stone
// even when the board cell is empty -- Score() evaluates a not-yet-placed
// candidate. Cells are returned in axis order; length is capped at 9.
int CAi::boatRunAxis(int x, int y, int p, int a, CPoint *cells) {
    int nx=x, ny=y;
    for (;;) {                          // walk to the negative end of the run
        int px=nx-dx[a], py=ny-dy[a];
        if (px<0 || px>=19 || py<0 || py>=19) break;
        int v = (px==x && py==y) ? p : bd[px][py];
        if (v!=p) break;
        nx=px; ny=py;
    }
    int n=0, cx=nx, cy=ny;              // collect forward across the run
    while (cx>=0 && cx<19 && cy>=0 && cy<19) {
        int v = (cx==x && cy==y) ? p : bd[cx][cy];
        if (v!=p) break;
        cells[n].x=cx; cells[n].y=cy;
        if (++n>=9) break;
        cx+=dx[a]; cy+=dy[a];
    }
    return n;
}

// First axis whose maximal run through (x,y) is >=5; fills cells and returns
// its length (0 if none). Used to record a pending five for the survived check.
int CAi::boatRun(int x, int y, int p, CPoint *cells) {
    for (int a=0; a<4; a++) {
        int n=boatRunAxis(x,y,p,a,cells);
        if (n>=5) return n;
    }
    return 0;
}

// True (proof) iff some >=5 run through (x,y) has NO pair-capturable stone.
// Pair-capturability is tested per the oracle on the post-move board, so the
// played point (x,y) counts as a p stone when it lands in a flank. Returns 1
// (keep the win) when no >=5 run is found, so a five the pattern table saw is
// never wrongly demoted; returns 0 only when every >=5 run is breakable.
int CAi::boatRunProof(int x, int y, int p) {
    CPoint run[9];
    int foundRun=0;
    for (int a=0; a<4; a++) {
        int n=boatRunAxis(x,y,p,a,run);
        if (n<5) continue;
        foundRun=1;
        int breakable=0;
        for (int c=0; c<n && !breakable; c++) {
            int sxc=run[c].x, syc=run[c].y;
            for (int k=0; k<8; k++) {
                int ax=sxc+dx[k],   ay=syc+dy[k];    // pair partner
                int fx=sxc+2*dx[k], fy=syc+2*dy[k];  // far flank
                int bx=sxc-dx[k],   by=syc-dy[k];    // near flank
                if (ax<0||ax>=19||ay<0||ay>=19) continue;
                if (fx<0||fx>=19||fy<0||fy>=19) continue;
                if (bx<0||bx>=19||by<0||by>=19) continue;
                int av=(ax==x&&ay==y)?p:bd[ax][ay];
                if (av!=p) continue;                 // no own neighbour -> no pair
                int fv=(fx==x&&fy==y)?p:bd[fx][fy];
                int bv=(bx==x&&by==y)?p:bd[bx][by];
                int fEnemy=(fv>0 && fv!=p), fEmpty=(fv<=0); // -1/0 = empty
                int bEnemy=(bv>0 && bv!=p), bEmpty=(bv<=0);
                if ((fEnemy && bEmpty) || (fEmpty && bEnemy)) { breakable=1; break; }
            }
            // Keryo/O-Pente: a run stone is ALSO breakable if a TRIPLE through it
            // is capturable (mirrors OPenteState.isGameOver's length-3 checks).
            // Same (x,y)-counts-as-p convention as the pair form above.
            if (cfg.captureTriples && !breakable)
                for (int k=0; k<8 && !breakable; k++) {
                    int p1x=sxc+dx[k],   p1y=syc+dy[k];      // +1k
                    int p2x=sxc+2*dx[k], p2y=syc+2*dy[k];    // +2k
                    int p3x=sxc+3*dx[k], p3y=syc+3*dy[k];    // +3k
                    int m1x=sxc-dx[k],   m1y=syc-dy[k];      // -1k
                    int m2x=sxc-2*dx[k], m2y=syc-2*dy[k];    // -2k
                    // END: S,+k,+2k own; flanks -k and +3k are {enemy, empty}
                    if (p1x>=0&&p1x<19&&p1y>=0&&p1y<19 &&
                        p2x>=0&&p2x<19&&p2y>=0&&p2y<19 &&
                        p3x>=0&&p3x<19&&p3y>=0&&p3y<19 &&
                        m1x>=0&&m1x<19&&m1y>=0&&m1y<19) {
                        int a1=(p1x==x&&p1y==y)?p:bd[p1x][p1y];
                        int a2=(p2x==x&&p2y==y)?p:bd[p2x][p2y];
                        if (a1==p && a2==p) {
                            int fv=(p3x==x&&p3y==y)?p:bd[p3x][p3y];
                            int bv=(m1x==x&&m1y==y)?p:bd[m1x][m1y];
                            int fEnemy=(fv>0&&fv!=p), fEmpty=(fv<=0);
                            int bEnemy=(bv>0&&bv!=p), bEmpty=(bv<=0);
                            if ((fEnemy&&bEmpty)||(fEmpty&&bEnemy)) { breakable=1; break; }
                        }
                    }
                    // CENTER: +k,-k own; flanks +2k and -2k are {enemy, empty}
                    if (p1x>=0&&p1x<19&&p1y>=0&&p1y<19 &&
                        m1x>=0&&m1x<19&&m1y>=0&&m1y<19 &&
                        p2x>=0&&p2x<19&&p2y>=0&&p2y<19 &&
                        m2x>=0&&m2x<19&&m2y>=0&&m2y<19) {
                        int a1=(p1x==x&&p1y==y)?p:bd[p1x][p1y];
                        int b1=(m1x==x&&m1y==y)?p:bd[m1x][m1y];
                        if (a1==p && b1==p) {
                            int fv=(p2x==x&&p2y==y)?p:bd[p2x][p2y];
                            int bv=(m2x==x&&m2y==y)?p:bd[m2x][m2y];
                            int fEnemy=(fv>0&&fv!=p), fEmpty=(fv<=0);
                            int bEnemy=(bv>0&&bv!=p), bEmpty=(bv<=0);
                            if ((fEnemy&&bEmpty)||(fEmpty&&bEnemy)) { breakable=1; break; }
                        }
                    }
                }
        }
        if (!breakable) return 1;       // a clean >=5 run -> genuine win
    }
    return foundRun ? 0 : 1;            // every >=5 run breakable -> provisional
}

