package mpi.fruitfly.registration;

import java.util.Iterator;
import java.util.Vector;
import java.util.Random;

public class TRModel extends Model {

	static public final int MIN_SET_SIZE = 2;
	
	private float[] translation;
	
	private float sin;
	private float cos;

	private final Vector<Match> inliers = new Vector<Match>();
	
	@Override
	float[] apply( float[] point )
	{
		float[] transformed = point.clone();
		
//		 rotate                                                                                                                                                                                              
	    transformed[ 0 ] = cos * point[ 0 ] - sin * point[ 1 ];
	    transformed[ 1 ] = sin * point[ 0 ] + cos * point[ 1 ];
	    
	    // translate                                                                                                                                                                                           
	    transformed[ 0 ] += translation[ 0 ];
	    transformed[ 1 ] += translation[ 1 ];

	    return transformed;
	}
	
	@Override
	boolean fit( Vector< Match > matches )
	{
		translation = new float[ 2 ];
		Match m1 = matches.get( 0 );
        Match m2 = matches.get( 1 );
        
        float x1 = m2.p1[ 0 ] - m1.p1[ 0 ];
	    float y1 = m2.p1[ 1 ] - m1.p1[ 1 ];
	    float x2 = m2.p2[ 0 ] - m1.p2[ 0 ];
	    float y2 = m2.p2[ 1 ] - m1.p2[ 1 ];
	    float l1 = ( float )Math.sqrt( x1 * x1 + y1 * y1 );
	    float l2 = ( float )Math.sqrt( x2 * x2 + y2 * y2 );
	                                                                                                                 
	    x1 /= l1;
	    x2 /= l2;
	    y1 /= l1;
	    y2 /= l2;
	                                                                                                                 
	    //! unrotate (x2,y2)^T to (x1,y1)^T = (1,0)^T getting the sinus and cosinus of the rotation angle            
	    cos = x1 * x2 + y1 * y2;
	    sin = x1 * y2 - y1 * x2;
	                                                                                                                 
	    //m.alpha = atan2( y, x );
	                                                                                                                 
	    //! rotate c1->f1                                                                                            
	    x1 = cos * m1.p1[ 0 ] - sin * m1.p1[ 1 ];
	    y1 = sin * m1.p1[ 0 ] + cos * m1.p1[ 1 ];
	                                                                                                                 
	    translation[ 0 ] = m1.p2[ 0 ] - x1;
	    translation[ 1 ] = m1.p2[ 1 ] - y1;
	                                                                                                                 
	    //cout << "fitted" << endl;
	                                                                                                                 
	    return true;
	}
	
	@Override
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		return ( "[3,3](" + cos + "," + -sin + "," + translation[ 0 ] + "," + sin + "," + cos + "," + translation[ 1 ] + ",0,0,1) " + error );
	}
	
	/** Returns dx, dy, rot, xo, yo from the two sets of points, where xo,yo is the center of rotation */
	static public float[] refine(final Vector< Match > matches)
	{
		// center of mass:
		float xo1 = 0, yo1 = 0;
		float xo2 = 0, yo2 = 0;
		// Implementing Johannes Schindelin's squared error minimization formula
		// tan(angle) = Sum(x1*y1 + x2y2) / Sum(x1*y2 - x2*y1)
		int length = matches.size();
		// 1 - compute centers of mass, for displacement and origin of rotation
		
		for ( Match m: matches )
		{
			xo1 += m.p1[ 0 ];
			yo1 += m.p1[ 1 ];
			xo2 += m.p2[ 0 ];
			yo2 += m.p2[ 1 ];
		}
		xo1 /= length;
		yo1 /= length;
		xo2 /= length;
		yo2 /= length;
		
		float dx = xo1 - xo2; // reversed, because the second will be moved relative to the first
		float dy = yo1 - yo2;
		float sum1 = 0, sum2 = 0;
		float x1, y1, x2, y2;
		for ( Match m : matches )
		{
			// make points local to the center of mass of the first landmark set
			x1 = m.p1[ 0 ] - xo1; // x1
			y1 = m.p1[ 1 ] - yo1; // x2
			x2 = m.p2[ 0 ] - xo2 + dx; // y1
			y2 = m.p2[ 1 ] - yo2 + dy; // y2
			sum1 += x1 * y2 - y1 * x2; //   x1 * y2 - x2 * y1 // assuming p1 is x1,x2, and p2 is y1,y2
			sum2 += x1 * x2 + y1 * y2; //   x1 * y1 + x2 * y2
		}
		float angle = ( float )Math.atan2( -sum1, sum2 );
		//angle = Math.toDegrees(angle);
		return new float[]{dx, dy, angle, xo1, yo1};
	}
	
	/**                                                                                           
	 * estimate the transformation model for a set                                                
	 * of feature correspondences using RANSAC                                                    
	 */
	static public TRModel estimateModel(
            Vector< Match > matches,
            int iterations,
            float epsilon,
            float min_inliers, 
            float[] tr)
	{
		if ( matches.size() < (int)( TRModel.MIN_SET_SIZE / min_inliers ) )
        	return null;
		
        Vector< TRModel > models = new Vector< TRModel >();

        TRModel model = new TRModel();        //!< the final model to be estimated                                      
        /* Made into an instance final field by Albert // Vector< Match > inliers = new Vector< Match >(); */
	final Vector< Match > inliers = model.inliers; // for local use in this static method

        //std::vector< FeatureMatch* > points;

	// get a new instance, so that Threads don't compete for it
	final Random random = new Random(69997);

        int i = 0;
        while ( i < iterations )                                                                  
        {                                                                                         
            // choose T::MIN_SET_SIZE disjunctive matches randomly                                
            Vector< Match > points = new Vector< Match >();
            Vector< Integer > keys = new Vector< Integer >();
            //int[] keys;
            for ( int j = 0; j < TRModel.MIN_SET_SIZE; ++j )
            {
            	int key;
                boolean in_set = false;
                do
                {
                    key = ( int )( random.nextDouble() * matches.size() );
                    in_set = false;
                    for ( Iterator k = keys.iterator(); k.hasNext(); )   
                    {
                    	Integer kk = ( Integer )k.next();
                        if ( key == kk.intValue() )
                        {
                            in_set = true;
                            break;
                        }
                    }
                }
                while ( in_set );
                keys.addElement( key );
                points.addElement( matches.get( key ) );
            }
            
            final TRModel m = new TRModel();
            m.fit( points );
 
            final Vector< Match > il = new Vector< Match >();

            if ( m.test( matches, epsilon, min_inliers, il ) > 0 && m.betterThan( model ) )
            {
		 /*
            	inliers = new Vector< Match >();
            	for ( Match ma : il )
            		inliers.addElement( ma );
		*/
            	model = m.clone();
		// faster:
		inliers.clear(); // does not seem logical that this is necessary; there must be heavily entangled code in all these c++-like method calls
		inliers.addAll(il);
		model.inliers.addAll(inliers);
		//System.out.println("size of inliers vector: " + model.inliers.size() + "\n\t il.size(): " + il.size());
            }
            ++i;

            points.clear();
        }                                                                                        
        if ( inliers.size() == 0 )
        	return null;
        
        System.out.println( "number of inliers: " + inliers.size() );
        float[] transform = refine( inliers );
        
        for (int j = 0; j < transform.length; j++)
        	tr[j] = transform[j];
        
        return model;
    }
	
	public TRModel clone()
	{
		TRModel trm = new TRModel();
		trm.cos = cos;
		trm.error = error;
		trm.sin = sin;
		trm.translation = translation.clone();
		// TODO: should also duplicate the inliers? ASK // so far, I just set it when cloning it, above
		return trm;
	}

	public Vector<Match> getInliers() {
		return this.inliers;
	}
}
