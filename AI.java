package jump61;

import java.util.ArrayList;
import java.util.Random;


import static jump61.Side.*;

/** An automated Player.
 *  @author P. N. Hilfinger
 */
class AI extends Player {

    /** A new player of GAME initially COLOR that chooses moves automatically.
     *  SEED provides a random-number seed used for choosing moves.
     */
    AI(Game game, Side color, long seed) {
        super(game, color);
        _random = new Random(seed);
    }

    /** Red winning value. */
    static final int RW = 10000000;

    /** Blue winning value. */
    static final int BW = -10000000;

    @Override
    String getMove() {
        Board board = getGame().getBoard();

        assert getSide() == board.whoseMove();
        int choice = searchForMove();
        getGame().reportMove(board.row(choice), board.col(choice));
        return String.format("%d %d", board.row(choice), board.col(choice));
    }

    /** Return a move after searching the game tree to DEPTH>0 moves
     *  from the current position. Assumes the game is not over. */
    private int searchForMove() {
        Board work = new Board(getBoard());
        assert getSide() == work.whoseMove();
        _foundMove = -1;
        if (getSide() == RED) {
            minMax(work, 3, true, 1, BW, RW);
        } else {
            minMax(work, 3, true, -1, BW, RW);
        }
        return _foundMove;
    }


    /** Find a move from position BOARD and return its value, recording
     *  the move found in _foundMove iff SAVEMOVE. The move
     *  should have maximal value or have value > BETA if SENSE==1,
     *  and minimal value or value < ALPHA if SENSE==-1. Searches up to
     *  DEPTH levels.  Searching at level 0 simply returns a static estimate
     *  of the board value and does not set _foundMove. If the game is over
     *  on BOARD, does not set _foundMove. */
    private int minMax(Board board, int depth, boolean saveMove,
                       int sense, int alpha, int beta) {
        Board work = new Board(board);
        if (depth == 0 || work.getWinner() != null) {
            return staticEval(board, RW);
        } else if (sense == 1) {
            int bestSoFar = BW;
            ArrayList<Integer> possMoves = movesList(work, work.whoseMove());
            for (int move : possMoves) {
                work.addSpot(work.whoseMove(), move);
                int response = minMax(work, depth - 1, false, -1, alpha, beta);
                if (response > bestSoFar) {
                    bestSoFar = response;
                    alpha = Math.max(alpha, bestSoFar);
                    if (saveMove) {
                        _foundMove = move;
                    }
                    if (alpha >= beta) {
                        return bestSoFar;
                    }
                }
                work.undo();
            }
            if (_foundMove == -1) {
                _foundMove = possMoves.get(0);
            }
            return bestSoFar;
        } else {
            ArrayList<Integer> possMoves = movesList(work, work.whoseMove());
            int bestSoFar = RW;
            for (int move : possMoves) {
                work.addSpot(work.whoseMove(), move);
                int response = minMax(work, depth - 1, false, 1, alpha, beta);
                if (response < bestSoFar) {
                    bestSoFar = response;
                    beta = Math.min(beta, bestSoFar);
                    if (saveMove) {
                        _foundMove = move;
                    }
                    if (alpha >= beta) {
                        return bestSoFar;
                    }
                }
                work.undo();
            }
            if (_foundMove == -1) {
                _foundMove = possMoves.get(0);
            }
            return bestSoFar;
        }
    }

    /** Return a heuristic estimate of the value of board position B.
     *  Use WINNINGVALUE to indicate a win for Red and -WINNINGVALUE to
     *  indicate a win for Blue. */
    private int staticEval(Board b, int winningValue) {
        ArrayList<Square> contents = b.getBoard();
        int heuristic = 0;
        if (b.getWinner() != null && b.getWinner().equals(RED)) {
            return winningValue;
        } else if (b.getWinner() != null && b.getWinner().equals(BLUE)) {
            return -1 * winningValue;
        } else {
            for (int k = 0; k < b.size(); k++) {
                if (contents.get(k).getSpots() == b.neighbors(k)
                        && b.isLegal(contents.get(k).getSide(), k)) {
                    heuristic++;
                }
            }
            return heuristic;
        }
    }

    /** Returns an ArrayList of all legal moves.
     * @param b board
     * @param player player
     * @return arraylist of moves */
    public ArrayList<Integer> movesList(Board b, Side player) {
        ArrayList<Integer> possMoves = new ArrayList<>();
        for (int k = 0; k < b.getBoard().size(); k++) {
            if (b.isLegal(player, k)) {
                possMoves.add(k);
            }
        } return possMoves;
    }

    /** A random-number generator used for move selection. */
    private Random _random;

    /** Used to convey moves discovered by minMax. */
    private int _foundMove;

}
