
package jump61;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Formatter;

import java.util.function.Consumer;

import static jump61.Side.*;

/** Represents the state of a Jump61 game.  Squares are indexed either by
 *  row and column (between 1 and size()), or by square number, numbering
 *  squares by rows, with squares in row 1 numbered from 0 to size()-1, in
 *  row 2 numbered from size() to 2*size() - 1, etc. (i.e., row-major order).
 *
 *  A Board may be given a notifier---a Consumer<Board> whose
 *  .accept method is called whenever the Board's contents are changed.
 *
 *  @author Will Harvey
 */
class Board {

    /** An uninitialized Board.  Only for use by subtypes. */
    protected Board() {
        _notifier = NOP;
    }

    /** An N x N board in initial configuration. */
    Board(int N) {
        this();
        _boardList = new ArrayList<Square>();
        for (int k = 0; k < N * N; k++) {
            _boardList.add(Square.square(WHITE, 1));
        }
        _undoHistory = new ArrayList<Board>();
        _size = N;
    }

    /** A board whose initial contents are copied from BOARD0, but whose
     *  undo history is clear, and whose notifier does nothing. */
    Board(Board board0) {
        this(board0.size());
        internalCopy(board0);
        _readonlyBoard = new ConstantBoard(this);
    }

    /** Returns a readonly version of this board. */
    Board readonlyBoard() {
        return _readonlyBoard;
    }

    /** (Re)initialize me to a cleared board with N squares on a side. Clears
     *  the undo history and sets the number of moves to 0. */
    void clear(int N) {
        copy(new Board(N));
        announce();
    }

    /** Copy the contents of BOARD into me. */
    void copy(Board board) {
        this._boardList = deepCopyBoard(board.getBoard());
        this._undoHistory = new ArrayList<>();
        this._notifier = board._notifier;
        this._size = board.size();
    }


        /** Copy the contents of BOARD into me, without modifying my undo
         *  history. Assumes BOARD and I have the same size. */
    private void internalCopy(Board board) {
        assert size() == board.size();
        this._boardList = deepCopyBoard(board.getBoard());
        this._undoHistory = deepCopyUndo(board.getUndo());
        this._notifier = board._notifier;
    }

    /** Return the number of rows and of columns of THIS. */
    int size() {
        return _size;
    }

    /** Returns the contents of the square at row R, column C
     *  1 <= R, C <= size (). */
    Square get(int r, int c) {
        return get(sqNum(r, c));
    }

    /** Returns the contents of square #N, numbering squares by rows, with
     *  squares in row 1 number 0 - size()-1, in row 2 numbered
     *  size() - 2*size() - 1, etc. */
    Square get(int n) {
        return _boardList.get(n);
    }

    /** Returns the total number of spots on the board. */
    int numPieces() {
        int numSpots = 0;
        for (Square squares : _boardList) {
            numSpots += squares.getSpots();
        } return numSpots;
    }

    /** Returns the Side of the player who would be next to move.  If the
     *  game is won, this will return the loser (assuming legal position). */
    Side whoseMove() {
        return ((numPieces() + size()) & 1) == 0 ? RED : BLUE;
    }

    /** Return true iff row R and column C denotes a valid square. */
    final boolean exists(int r, int c) {
        return 1 <= r && r <= size() && 1 <= c && c <= size();
    }

    /** Return true iff S is a valid square number. */
    final boolean exists(int s) {
        int N = size();
        return 0 <= s && s < N * N;
    }

    /** Return the row number for square #N. */
    final int row(int n) {
        return n / size() + 1;
    }

    /** Return the column number for square #N. */
    final int col(int n) {
        return n % size() + 1;
    }

    /** Return the square number of row R, column C. */
    final int sqNum(int r, int c) {
        return (c - 1) + (r - 1) * size();
    }

    /** Return a string denoting move (ROW, COL)N. */
    String moveString(int row, int col) {
        return String.format("%d %d", row, col);
    }

    /** Return a string denoting move N. */
    String moveString(int n) {
        return String.format("%d %d", row(n), col(n));
    }

    /** Returns true iff it would currently be legal for PLAYER to add a spot
        to square at row R, column C. */
    boolean isLegal(Side player, int r, int c) {
        return isLegal(player, sqNum(r, c));
    }

    /** Returns true iff it would currently be legal for PLAYER to add a spot
     *  to square #N. */
    boolean isLegal(Side player, int n) {
        return get(n).getSide().equals(player)
                || get(n).getSide().equals(WHITE);
    }

    /** Returns true iff PLAYER is allowed to move at this point. */
    boolean isLegal(Side player) {
        if (getWinner() != null) {
            return false;
        } else {
            return whoseMove().equals(player);
        }
    }

    /** Returns the winner of the current position, if the game is over,
     *  and otherwise null. */
    final Side getWinner() {
        if (numOfSide(RED) == size() * size()) {
            return RED;
        } else if (numOfSide(BLUE) == size() * size()) {
            return BLUE;
        } else {
            return null;
        }
    }

    /** Return the number of squares of given SIDE. */
    int numOfSide(Side side) {
        int number = 0;
        for (Square square : _boardList) {
            if (square.getSide().equals(side)) {
                number++;
            }
        } return number;
    }

    /** Add a spot from PLAYER at row R, column C.  Assumes
     *  isLegal(PLAYER, R, C). */
    void addSpot(Side player, int r, int c) {
        addSpot(player, sqNum(r, c));
    }

    /** Add a spot from PLAYER at square #N.  Assumes isLegal(PLAYER, N). */
    void addSpot(Side player, int n) {
        if (!isLegal(player, n)) {
            throw new GameException("move is invalid");
        } else if (!exists(n)) {
            throw new GameException("square location is invalid");
        } else if (!isLegal(player)) {
            throw new GameException("game is over or its not your turn");
        } else {
            markUndo();
            simpleAdd(player, n, 1);
            if (overfull(n)) {
                jump(n, player);
            }
        }
    }

    /** Set the square at row R, column C to NUM spots (0 <= NUM), and give
     *  it color PLAYER if NUM > 0 (otherwise, white). */
    void set(int r, int c, int num, Side player) {
        internalSet(r, c, num, player);
        announce();
    }

    /** Set the square at row R, column C to NUM spots (0 <= NUM), and give
     *  it color PLAYER if NUM > 0 (otherwise, white).  Does not announce
     *  changes. */
    private void internalSet(int r, int c, int num, Side player) {
        internalSet(sqNum(r, c), num, player);
    }

    /** Set the square #N to NUM spots (0 <= NUM), and give it color PLAYER
     *  if NUM > 0 (otherwise, white). Does not announce changes. */
    private void internalSet(int n, int num, Side player) {
        if (num > 0) {
            _boardList.set(n, Square.square(player, num));
        } else {
            _boardList.set(n, Square.square(WHITE, num));
        }
    }


    /** Undo the effects of one move (that is, one addSpot command).  One
     *  can only undo back to the last point at which the undo history
     *  was cleared, or the construction of this Board. */
    void undo() {
        int lastMove = _undoHistory.size() - 1;
        internalCopy(_undoHistory.get(lastMove));
    }

    /** Record the beginning of a move in the undo history. */
    private void markUndo() {
        _undoHistory.add(new Board(this));
    }

    /** Add DELTASPOTS spots of side PLAYER to row R, column C,
     *  updating counts of numbers of squares of each color. */
    private void simpleAdd(Side player, int r, int c, int deltaSpots) {
        internalSet(r, c, deltaSpots + get(r, c).getSpots(), player);
    }

    /** Add DELTASPOTS spots of color PLAYER to square #N,
     *  updating counts of numbers of squares of each color. */
    private void simpleAdd(Side player, int n, int deltaSpots) {
        internalSet(n, deltaSpots + get(n).getSpots(), player);
    }

    /** Used in jump to keep track of squares needing processing.  Allocated
     *  here to cut down on allocations. */
    private final ArrayDeque<Integer> _workQueue = new ArrayDeque<>();

    /** Do all jumping on this board, assuming that initially, S is the only
     *  square that might be over-full.
     *  @param player player
     *  @param S square*/
    private void jump(int S, Side player) {
        neighborList(S);
        internalSet(S, 1, player);
        if (getWinner() != null) {
            _workQueue.clear();
        }
        while (!_workQueue.isEmpty()) {
            int s = _workQueue.pop();
            simpleAdd(player,  s, 1);
            if (overfull(s)) {
                jump(s, player);
            }
        }
    }

    /** Returns my dumped representation. */
    @Override
    public String toString() {
        Formatter out = new Formatter();
        out.format("===");
        for (int i = 0; i < size() * size(); i++) {
            if (i % size() == 0) {
                out.format("\n");
                out.format("    ");
            }
            out.format("%d", _boardList.get(i).getSpots());
            if (_boardList.get(i).getSide().equals(RED)) {
                out.format("r ");
            } else if (_boardList.get(i).getSide().equals(BLUE)) {
                out.format("b ");
            } else {
                out.format("- ");
            }
        } out.format("\n");
        out.format("===");
        return out.toString();
    }

    /** Returns an external rendition of me, suitable for human-readable
     *  textual display, with row and column numbers.  This is distinct
     *  from the dumped representation (returned by toString). */
    public String toDisplayString() {
        String[] lines = toString().trim().split("\\R");
        Formatter out = new Formatter();
        for (int i = 1; i + 1 < lines.length; i += 1) {
            out.format("%2d %s%n", i, lines[i].trim());
        }
        out.format("  ");
        for (int i = 1; i <= size(); i += 1) {
            out.format("%3d", i);
        }
        return out.toString();
    }

    /** Returns the number of neighbors of the square at row R, column C. */
    int neighbors(int r, int c) {
        int size = size();
        int n;
        n = 0;
        if (r > 1) {
            n += 1;
        }
        if (c > 1) {
            n += 1;
        }
        if (r < size) {
            n += 1;
        }
        if (c < size) {
            n += 1;
        }
        return n;
    }

    /** Returns the number of neighbors of square #N. */
    int neighbors(int n) {
        return neighbors(row(n), col(n));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Board)) {
            return false;
        } else {
            Board B = (Board) obj;
            return this._undoHistory.equals(B._undoHistory)
                    && this._notifier.equals(B._notifier)
                    && this._boardList.equals(B._boardList)
                    && this._size == B._size;
        }
    }

    @Override
    public int hashCode() {
        return numPieces();
    }

    /** Set my notifier to NOTIFY. */
    public void setNotifier(Consumer<Board> notify) {
        _notifier = notify;
        announce();
    }

    /** Take any action that has been set for a change in my state. */
    private void announce() {
        _notifier.accept(this);
    }

    /** Performs a deepcopy of an ArrayList of Squares.
     * @return ArrayList of squares
     * @param lst ArrayList*/
    private ArrayList<Square> deepCopyBoard(ArrayList<Square> lst) {
        if (lst == null) {
            return null;
        } else {
            return new ArrayList<>(lst);
        }
    }

    /** Performs a deepcopy of an ArrayList of Boards.
     * @return ArrayList of boards
     * @param lst ArrayList*/
    private ArrayList<Board> deepCopyUndo(ArrayList<Board> lst) {
        if (lst == null) {
            return null;
        } else {
            return new ArrayList<>(lst);
        }
    }

    /** Checks if a #N square is overfull. Returns yes if it is overfull.
     * @return true if overfull false if not
     * @param n int*/
    public boolean overfull(int n) {
        return _boardList.get(n).getSpots() > neighbors(n);
    }

    /** Getter method for _board.
     * @return current board */
    public ArrayList<Square> getBoard() {
        return _boardList;
    }

    /** Getter Method for _undoHistory.
     * @return current undo history*/
    public ArrayList<Board> getUndo() {
        return _undoHistory;
    }

    /** Updates _workQueue with the neighbors of a square.
     * @param n square*/
    public void neighborList(int n) {
        if (neighbors(n) == 2) {
            if (n == 0) {
                _workQueue.add(1);
                _workQueue.add(size());
            } else if (n == size() - 1) {
                _workQueue.add(n - 1);
                _workQueue.add(n + size());
            } else if (n == size() * size() - 1) {
                _workQueue.add(n - 1);
                _workQueue.add(n - size());
            } else {
                _workQueue.add(n + 1);
                _workQueue.add(n - size());
            }
        } else if (neighbors(n) == 3) {
            if (n % size() == 0) {
                _workQueue.add(n + 1);
                _workQueue.add(n + size());
                _workQueue.add(n - size());
            } else if (n < size()) {
                _workQueue.add(n + 1);
                _workQueue.add(n - 1);
                _workQueue.add(n + size());
            } else if (n % size() == size() - 1) {
                _workQueue.add(n - 1);
                _workQueue.add(n + size());
                _workQueue.add(n - size());
            } else {
                _workQueue.add(n + 1);
                _workQueue.add(n - size());
                _workQueue.add(n - 1);
            }
        } else {
            _workQueue.add(n + 1);
            _workQueue.add(n + size());
            _workQueue.add(n - size());
            _workQueue.add(n - 1);
        }
    }

    /** A notifier that does nothing. */
    private static final Consumer<Board> NOP = (s) -> { };

    /** A read-only version of this Board. */
    private ConstantBoard _readonlyBoard;

    /** Use _notifier.accept(B) to announce changes to this board. */
    private Consumer<Board> _notifier;

    /** List of undo history. */
    private ArrayList<Board> _undoHistory;

    /** Size of board (# of rows/columns. */
    private int _size;

    /** Current Board. */
    private ArrayList<Square> _boardList;

}
