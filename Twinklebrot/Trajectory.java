import java.util.Random;

// This is a pure data class and static utility methods 
// that should not import other otherwise depend upon any specific application.

/**
 * Represents a single Mandelbrot trajectory.
 * Given a complex C and an initial Z, and constraints,
 * produces all points along the resulting trajectory.
 * Also manages a "current" cursor that starts at a given index
 * and can be advanced via the advance() method.
 * 
 * @author Melinda Green
 */
public class Trajectory {
    public final static int MAX_MAX = 100000; // Largest max value allowed.
    private final static double BIG = 2; // Must be at least 2.
    private final static double BIG2 = BIG * BIG;
    private final static Random rnd = new Random(0);
    private final static double[] TMP_COORDS = new double[3 * MAX_MAX]; // Scratch space.
    private double zr, zi, cr, ci;
    private int cur, maxiters;
    private double[] packedPoints;

    public Trajectory(double cr, double ci, double zr, double zi, int start, int maxiters, double max2) {
        this.zr = zr;
        this.zi = zi;
        this.cr = cr;
        this.ci = ci;
        this.cur = start;
        this.maxiters = maxiters;
        this.packedPoints = mandel(zr, zi, cr, ci, maxiters, max2);
    }
    public double getCr() {
        return cr;
    }
    public double getCi() {
        return ci;
    }
    public int getStart() {
        return cur;
    }
    public void setStart(int start) {
        this.cur = start;
    }
    public int length() {
        return packedPoints == null ? 0 : packedPoints.length / 2;
    }
    public boolean escapes() {
        int length = length();
        return length < maxiters;
    }
    public double[] getPackedPoints() {
        return packedPoints;
    }
    public boolean canAdvance() {
        return cur < packedPoints.length / 2;
    }
    public void advance() {
        if(!canAdvance())
            System.err.println("Advanced too far.");
        cur++;
    }
    private double amplitudeGuess = -1;

    public double guessAmplitude() {
        if(amplitudeGuess >= 0)
            return amplitudeGuess;
        int samples = 10;
        if(length() < samples + 2)
            return 0;
        int mid = length() / 2;
        int start = mid - samples / 2;
        double xmin = Double.MAX_VALUE, xmax = Double.MIN_NORMAL;
        for(int i = 0; i < samples; i++) {
            xmin = Math.min(xmin, packedPoints[start + 2 * i]);
            xmax = Math.max(xmax, packedPoints[start + 2 * i]);
        }
        double amplitudeGuess = xmax - xmin;
        return amplitudeGuess;
    }


    /**
     * Note: If in_mset is true, min is ignored and the returned Trajectory length will be exactly max.
     * If random_start is true, a random point in the first half of the path is set.
     * 
     * @return a Trajectory satisfying the given constraints.
     **/
    public static Trajectory makeTrajectory(int min, int max, boolean in_mset, boolean random_start) {
        Trajectory candidate = null;
        int len = Integer.MAX_VALUE;
        boolean len_ok = false;
        while(candidate == null || !len_ok) {
            // Generate a random trajectory starting point.
            double cr = rnd.nextDouble() * 2 * (rnd.nextBoolean() ? 1 : -1);
            double ci = rnd.nextDouble() * 2 * (rnd.nextBoolean() ? 1 : -1);
            // Perform a quick reject test against the largest bulbs for b-brot candidates.
            int period = bulbPeriod(cr, ci);
            if(!in_mset && period > 0)
                continue;
            if(in_mset) {
                // Filter out bulbs user is not interested in.
                boolean bulb_ok = false;
                for(int bulb = 0; bulb <= 4 && !bulb_ok; bulb++) {
                    if(PropertyManager.getBoolean(BulbControls.PREFIX + bulb, true))
                        bulb_ok = true;
                }
                if(!bulb_ok) {
                    continue;
                }
            }
            candidate = new Trajectory(cr, ci, 0, 0, 0, max, BIG2);
            len = candidate.length();
            len_ok = in_mset ? (len == max) : (min <= len && len < max);
        }
        if(random_start) {
            int st = len / 2;
            candidate.setStart(rnd.nextInt(Math.max(st, 1)));
        }
        return candidate;
    }
    /**
     * Determines whether the given complex C is in or presumed to be in the m-set.
     * 
     * @param cr C real
     * @param ci C imaginary
     * @param maxIter maximum iterations before concluding in the m-set.
     * @param max2 square of exit threshold distance (typically 4).
     * @return iteration count for C to exit, or -1 if C is in the m-set or does not exit in maxIter iterations.
     */
    public static int quickMandelbrot(
        double cr,
        double ci,
        final long maxIter,
        final double max2)
    {
        if(bulbPeriod(cr, ci) > 0)
            return -1;
        int iter = 0;
        double zr = 0;
        double zi = 0;
        double temp;
        while((iter < maxIter) && ((zr * zr + zi * zi) < max2)) {
            temp = zr * zi;
            zr = zr * zr - zi * zi + cr;
            zi = temp + temp + ci;
            iter++;
        }
        return iter >= maxIter ? -1 : iter;
    }

    /**
     * Quick test to see if a given C is within one of the large bulbs.
     * 
     * Original version lifted from isInMSet() by ker2x posted at
     * http://www.fractalforums.com/programming/buddhabrot-optimizations
     * 
     * @return the bulb number if determined to be in a large bulb, 0 otherwise.
     */
    public static int bulbPeriod(double cr, double ci) {
        double ci2 = ci * ci;
        // Test for c in 2nd order period bulb (head)
        if((cr + 1.0) * (cr + 1.0) + ci2 < 0.0625)
            return 2;
        // Test for c in main cardioid
        double q = (cr - 0.25) * (cr - 0.25) + ci2;
        if(q * (q + (cr - 0.25)) < 0.25 * ci2)
            return 1;
        // Test for smaller bulb left of the period-2 bulb (top knot)
        if((((cr + 1.309) * (cr + 1.309)) + ci * ci) < 0.00345)
            return 4;
        // Test for smaller bulbs on top and bottom of the cardioid (hands)
        if((((cr + 0.125) * (cr + 0.125)) + (ci - 0.744) * (ci - 0.744)) < 0.0088)
            return 3;
        if((((cr + 0.125) * (cr + 0.125)) + (ci + 0.744) * (ci + 0.744)) < 0.0088)
            return 3;
        return 0;
    }


    public static double[] mandel(
        double zr, double zi,
        double cr, double ci,
        int max_iterations, double max2)
    {
        // This is the main Mandelbrot loop.
        for(int i = 0; i < max_iterations; i++) {
            /* compute and save the squares of z components */
            double r_sqrd = zr * zr;
            double i_sqrd = zi * zi;
            /*
             * Always exit when z diverges.
             * Note: the natural test would have been:
             * if(COMPLEX_NRMSQRD(local_z) > BIG2)
             * except that we need to save the above intermediate results
             * for later, so we use this equivalent but less readable test:
             */
            if(r_sqrd + i_sqrd > BIG2) {
                double[] ret = new double[2 * i];
                System.arraycopy(TMP_COORDS, 0, ret, 0, 2 * i);
                return ret;
            }
            /*
             * Perform one iteration.
             * What we want is z = z^2 + c which could be computed with:
             * COMPLEX_SQR(temp, local_z);
             * COMPLEX_ADD(local_z, temp, local_c);
             * The following lines do the same thing but avoids recomputation
             * of intermediate results which is slightly more efficient:
             */
            zi = zr * zi * 2 + ci;
            zr = r_sqrd - i_sqrd + cr;
            TMP_COORDS[2 * i + 0] = zr;
            TMP_COORDS[2 * i + 1] = zi;
        } // end main loop
        double[] ret = new double[2 * max_iterations];
        System.arraycopy(TMP_COORDS, 0, ret, 0, 2 * max_iterations);
        return ret;
//        return null;
    }


}
