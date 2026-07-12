// Synced from react_mmai/MMAIWASM at commit 03be73b.
// DO NOT EDIT HERE — edit upstream & re-sync
// via dsg_src/mmai/sync_from_upstream.sh.
//
#include "CPoint.h"

#ifndef CAI
#define CAI

// Rule knobs for the game variants the engine can host. Populated once in
// the CAi constructor from the canonical server game ID (GridStateFactory):
// Pente=1, Keryo=3, Poof=11, Connect6=13, Boat=15, O-Pente=25; even IDs are
// Speed twins with identical board rules. Legacy callers passing 2 get Keryo.
struct VariantConfig {
	bool capturePairs = true;       // gates capture MECHANICS in dmov/Tree only; Score/Eval
	                                // still score capture patterns unconditionally, so
	                                // Connect6 (false) is not playable until plan §4 lands
	bool captureTriples = false;    // Keryo/O-Pente triple capture
	int  capWinCount = 10;          // captured stones needed to win
	bool poofPairs = false;         // Poof/O-Pente 2-stone poof; not consumed yet
	bool poofTriples = false;       // O-Pente 3-stone poof; not consumed yet
	bool boatWin = false;           // Boat/O-Pente provisional five; not consumed yet
	int  winRowLength = 5;          // Connect6: 6; not consumed yet (pattern tables are 5-based)
	int  stonesPerTurn = 1;         // Connect6: 2; not consumed yet
	bool tournamentOpening = true;  // feeds 'tourn', which the engine currently never reads
};

// Mobile portability hook seam. A host (Android NDK wrapper, future iOS
// Objective-C++ shim) subclasses this and installs it via CAi::setListener to
// observe the search. Mirrors the OLD Android engine's two JNI callbacks:
//   * aiEvaluated()  -- old fired obj.aiEvaluatedCallBack() at the top of every
//                       Eval(), UNCONDITIONALLY (not gated by the mask).
//   * aiVisualization(data,len) -- old fired obj.aiVisualizationCallBack(int[])
//                       at the top of Eval(), gated by the callbacks toggle,
//                       with a flattened 19x19 board (data[x*19+y]==3 marks the
//                       cell being evaluated). len is always 361 here.
// When no listener is installed the engine makes NO calls and NO allocation, so
// the WASM/default path is byte-identical to before this seam existed.
struct CAiListener {
	virtual void aiEvaluated() {}
	virtual void aiVisualization(const int *data, int len) {}
	virtual ~CAiListener() {}
};

// Connect6 v3 forcing-search prover (docs/connect6-v3-vcf-spec.md). One
// whole-board scan (c6ScanFives) fills this: pSix=a six(P) window exists;
// oppImm=O can six in one 2-stone turn (window pc==0 && oc>=4); pFour=P can
// six this turn (window oc==0 && pc>=4, "canSixNow(P)"); nDistinct=|C(P)|, the
// count of DISTINCT five(P) completion cells, with the first two recorded in
// c1,c2 (used only when nDistinct==2). Same geometry/counting as v2.
struct C6Scan { int nDistinct; CPoint c1, c2; bool pSix, pFour, oppImm; };
enum { C6_PAIR_MAX = 12, C6_SCAN_BUDGET = 3000 };

class CAi {

// Construction
public:
	// filesDir: directory holding pente.tbl / pente.scs / opngbk.pen. Defaults to
	// "files" so the WASM/emscripten embed path is unchanged; the mobile wrappers
	// pass their app resource dir. A trailing '/' is optional (handled either way).
	CAi(int game1, int lvl, bool openingBook1, const char *filesDir = "files");

// Attributes
protected:
	int level;
	int seat;

    short *scores, *table, *obk;
    int *pFk, *pFh, *pTr, *pTm, *pTo, *pTi, *pfhn;

	unsigned long moveNum;
	static const int size = 19;

	static const int bsize = 912;
	static const int tsize = 943;

	static const int openingBookSize = 600;

	int cp;
	unsigned long tn;

	int obfl, cob, crot, obsize, extnt;
	int p[7], ccc[18][7], sx[362], sy[362];

	int dx[8] = {-1,0,1,-1,1,0,-1,1};
	int dy[8] = {-1,-1,-1,0,1,1,1,0};
	int rotx[8] = {1,1,1,1,-1,-1,-1,-1};
	int roty[8] = {1,1,-1,-1,-1,-1,1,1};
	int rotf[8] = {0,1,0,1,0,1,0,1};
	//int mxnd[] = {0, 20, 18, 16, 14, 12, 12, 12, 12, 12, 8,8,8,8,8,8, 8,8,8};
	int mxvt[19]={0,1,3,4,6,7,8,9,10,12,13,14,15, 18,18,18,18,18,18};
	int mxvf[19]={0,1,4,5,7,8,10,11,13,15,16,17,18, 18,18,18,18,18,18};

	int xoff, yoff, rlct;
	int rlst[800], rrot[800], oscr[openingBookSize], nom[openingBookSize];
	int brd[18][size][size];//, ciel[7][18];
	// 18 levels of 19x19 board
	// each ply of the search is a level
	// 0=empty, 1=player 1 stone, 2=player 2;
	// -1=empty space within 2 spaces of a stone
	// the computer will only consider moving to '-1'.
	int bmove, bscr;



	const int om2[8] = {181,182,162,163,164,165,144,145};
	const int op2[8] = {25,36,77,82,93,95,97,99};
	const int om3[32] = {183,184,202,221,240,260,239,238,237,256,236,235,
	    234,252,215,196,177,176,158,139,120,100,
	    121,122,123,104,124,125,126,108,145,164};

	int mxst=2, gf=0,
	multipbem=0, np=2;

	VariantConfig cfg;
	unsigned long turn;

	int fr, fhn, en, cap1, tourn;
	int mvct, lvl, ppd, vct, ferr, alpha, beta, a1, b1, erfl;
	int sco[7], plv;

	unsigned char *pHashD;
	short int *pHashS;
	int exfl[20], exel[20];
	int maxscr, extent, breadth;
	unsigned int *pHashY, HValX[20], HValY[20],
	TableX[362], TableY[362];
	int bd[19][19], cc[20][7], p1d[24], p2d[24], p3d[24], cap2, cap3;

	CPoint p1xy[24], pxy[24], p2xy[24], p3xy[24];
	CPoint pPxy[17]; int pPd[17]; int capP; // poof: own stones vanishing with the played stone (<=16 with triples)
	int capPf; // poof: per-form referee bonus (pairFired+tripleFired, 0-2) set alongside capP;
	           // consumed only in Score()'s own tail (same call that set it) so, unlike capP,
	           // it needs no Eval() tcapP-style snapshot -- nested rescore Score() calls never read it.
	int rowWin;                           // boat: Score() saw an (unclamped) >=5 row win this call
	int pendN[20]; CPoint pendC[20][9];   // boat: provisional-run cells recorded per ply

	// Connect6 (cfg.stonesPerTurn==2): tn at Move() entry, used by Tree() to
	// rotate the player from the global 0-based stone index via ownerOf().
	int tnRoot;
	// Connect6 win-in-pair gate: true when the stone being placed at the current
	// ply (global index g = tnRoot-2+lvl, owner == fr) is the FIRST of a turn's
	// two stones, i.e. ownerOf(g+1)==ownerOf(g)==fr, so a same-turn second stone
	// is still to come. Set beside every fr assignment in Tree() (push + backtrack)
	// so it always matches the current mover; read by Score6() to decide whether a
	// length-6 window with 5 mover stones + 1 empty is an immediate (this-turn) win.
	int c6NextSame = 0;
	int c6FallbackHits = 0;               // Connect6: times the defensive 2nd-stone fallback fired
	// Connect6 v3 instrumentation: number of times the c6ForcedWin terminal
	// proved a win (covering base OR deeper forcing recursion). Not reset per
	// getMove (unlike c6FallbackHits) so a harness can sum it across a whole game
	// to gauge how often the forcing terminal is load-bearing in real play.
	long c6ForceHits = 0;
	// Connect6 v2: master switch for the static covering-threat terminal
	// (c6UnstoppableThreat). Default true (enabled). Tests build a second engine
	// with this false to prove every new win/defense is load-bearing on the
	// terminal (the flag mutation gate); also a one-switch production kill.
	bool c6ForceEnabled = true;
	// Connect6 v3: whole-board scan budget for one c6ForcedWin call (reset at
	// entry). Decrement-before-work in c6And/c6Or; exhaustion => REFUSE. Valid
	// only within a single top-level call (Tree is single-threaded per CAi).
	int c6Budget = 0;


	int ciel[20][7], mxnd[20], sec[3];

	short int *pAs, *pAt;//*pPbk, *pKbk, *pPNm, *pPOs, *pKNm, *pKOs;

	// --- mobile portability seam state (all inert unless a host opts in) ---
	// stopfl is cleared once per getMove() call (see reset()), not per stone.
	// Connect6 plays 2 stones in a single getMove() turn (cfg.stonesPerTurn==2);
	// a requestStop() mid-turn therefore intentionally aborts BOTH stone
	// searches -- this is a whole-turn abort, not a per-stone cancellation.
	// There is no "cancelled" sentinel in the return value: a caller that
	// needs to know whether a given getMove() result reflects a completed
	// search or a stop must track that itself (e.g. note before calling
	// getMove() whether requestStop() had been invoked).
	volatile int stopfl = 0;          // requestStop() sets 1; Tree() bails; cleared at getMove()/reset()
	CAiListener *listener = nullptr;   // hook target; nullptr => no calls, no allocation
	int callbackMask = 0;              // mirrors old 'callbacks'; gates the visualization callback
	int loadErr = 0;                   // set to 1 by the ctor if pente.tbl/pente.scs/opngbk.pen failed to open


// Operations
public:
	int getMove(int *moves, int count);

	// True unless the ctor failed to open one of pente.tbl / pente.scs /
	// opngbk.pen under filesDir (in which case the corresponding table(s)
	// are left uninitialized and the engine may misbehave). Behavior is
	// otherwise unchanged from before this flag existed -- the ctor does not
	// abort or throw; callers that care should check ok() themselves.
	bool ok() const { return !loadErr; }

	// --- mobile portability seam (opt-in; inert for WASM / default callers) ---
	// Async stop: a host thread calls requestStop() while getMove() runs on the
	// engine thread; Tree() notices at its coarse per-node boundary and bails out
	// returning the current best legal move (old engine's AiWrapper stop(ptr) ->
	// cai->stopped=1, checked in the search loop). Cleared at getMove() entry.
	void requestStop() { stopfl = 1; }
	// Install / remove the hook listener (nullptr => no callbacks, no overhead).
	void setListener(CAiListener *l) { listener = l; }
	// Mirrors old toggleCallbacks(): non-zero enables the per-Eval visualization
	// callback; aiEvaluated() fires whenever a listener is set regardless of mask.
	void setCallbackMask(int m) { callbackMask = m; }

	// void setUseOpeningBook(bool book);
	// void setLevel(int lvl);
	// note: no setGame — cfg is derived from the game ID once, in the
	// constructor; construct a fresh CAi to switch variants.

protected:
	static VariantConfig configFor(int gameId);
	// Owner (1/2) of the stone at 0-based global index idx. Connect6 packs two
	// stones per turn (idx%4 in {0,3} -> P1, else P2); every other variant
	// strictly alternates. NOTE: not static (unlike configFor) because it reads
	// cfg.stonesPerTurn; the spec's "member static" wording can't hold here.
	int ownerOf(int idx);
	int Tree();
	int Eval(int x, int y);
	int Score(CPoint pt);
	int Score6(CPoint pt);   // Connect6: direct 6-window eval, bypasses 5-based tables
	bool c6UnstoppableThreat(int P); // Connect6 v2: static covering-threat forced-win terminal (now a c6ScanFives wrapper)
	// Connect6 v3 forcing-search prover (docs/connect6-v3-vcf-spec.md). Pure
	// w.r.t. engine state: c6And/c6Or place/undo scratch stones on bd (exact
	// 0-vs-(-1) save/restore) and touch no other engine field, the TT, or rand.
	C6Scan c6ScanFives(int P);                                   // one-pass scan (shared with v2)
	int    c6GenOpenFivePairs(int P, int *out, int max); // OR candidates, packed a*361+b (a<b); a*361+b needs int (>65535)
	bool   c6And(int P, int d);                                 // AND node: bases + unique-reply recursion
	bool   c6Or(int P, int d);                                  // OR node: canSixNow + open-five pair enumeration
	bool   c6ForcedWin(int P, int lvl);                         // top level: v2 fast path + budget + K ladder
	int boatRunAxis(int x, int y, int p, int a, CPoint *cells); // boat: maximal own run on one axis
	int boatRun(int x, int y, int p, CPoint *cells);            // boat: first axis whose run is >=5
	int boatRunProof(int x, int y, int p);                      // boat: five has no capturable stone?
	void reset();
	int Move();
	void dmov();
	int cmove();    
	void addMove(int move, int count);
// void Print();

// Implementation
public:
	virtual ~CAi();

protected:

};

#endif
