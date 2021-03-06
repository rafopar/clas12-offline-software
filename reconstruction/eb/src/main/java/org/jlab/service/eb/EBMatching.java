package org.jlab.service.eb;

import java.util.ArrayList;
import java.util.List;
import org.jlab.clas.detector.DetectorParticle;
import org.jlab.clas.detector.DetectorResponse;
import org.jlab.detector.base.DetectorType;

/*
 *
 * Currently just used for matching for neutrals.
 *
 * @author baltzell
 */
public class EBMatching {

    private EventBuilder eventBuilder=null;

    public EBMatching(EventBuilder eb) {
        this.eventBuilder=eb;
    }

    /*
     * addResponsesFTOF
     *
     * Find and add all unmatched, matching FTOF responses to each particle.
     *
     */
    public void addResponsesFTOF(List <DetectorParticle> parts) {

        List<DetectorResponse> respFTOF1A = eventBuilder.getUnmatchedResponses(null, DetectorType.FTOF, 1);
        List<DetectorResponse> respFTOF1B = eventBuilder.getUnmatchedResponses(null, DetectorType.FTOF, 2);
        List<DetectorResponse>  respFTOF2 = eventBuilder.getUnmatchedResponses(null, DetectorType.FTOF, 3);

        for (int ii=0; ii<parts.size(); ii++) {
            
            DetectorParticle part=parts.get(ii);

            int index1A = part.getDetectorHit(respFTOF1A, DetectorType.FTOF, 1, EBConstants.FTOF_MATCHING_1A);
            if (index1A >= 0) {
                part.addResponse(respFTOF1A.get(index1A), true);
                respFTOF1A.get(index1A).setAssociation(ii);
            }
            int index1B = part.getDetectorHit(respFTOF1B, DetectorType.FTOF, 2, EBConstants.FTOF_MATCHING_1B);
            if (index1B >= 0) {
                part.addResponse(respFTOF1B.get(index1B), true);
                respFTOF1B.get(index1B).setAssociation(ii);
            }
            // only try to match with FTOF2 if not matched with 1A/1B:
            if (index1A<0 && index1B<0) {
                int index2 = part.getDetectorHit(respFTOF2, DetectorType.FTOF, 3, EBConstants.FTOF_MATCHING_2);
                if (index2>=0) {
                    part.addResponse(respFTOF2.get(index2), true);
                    respFTOF2.get(index2).setAssociation(ii);
                }
            }
        }
    }

    /*
     * addResponsesECAL
     *
     * Find and add unmatched, matching ECAL responses to each particle,
     * 
     * But only for the given ECAL layers.
     *
     */
    public void addResponsesECAL(List <DetectorParticle> parts, int[] layers) {

        for (int ii=0; ii<parts.size(); ii++) {

            DetectorParticle part=parts.get(ii);
            
            for (int layer : layers) {

                double matching;
                switch (layer) {
                    case (1):
                        matching=EBConstants.PCAL_MATCHING;
                        break;
                    case (4):
                        matching=EBConstants.ECIN_MATCHING;
                        break;
                    case (7):
                        matching=EBConstants.ECOUT_MATCHING;
                        break;
                    default:
                        throw new RuntimeException("Invalid ECAL Layer:  "+layer);
                }

                List<DetectorResponse> respECAL = eventBuilder.getUnmatchedResponses(null, DetectorType.ECAL, layer);

                int index = part.getDetectorHit(respECAL, DetectorType.ECAL, layer, matching);
                if (index>=0) {
                    part.addResponse(respECAL.get(index), true); 
                    respECAL.get(index).setAssociation(ii);
                }
            }
        }
    }

    /*
     * findNeutrals
     *
     * Create neutral particles from unmatched responses from the given ECAL layer.
     *
     * Add unmatched, matching responses from FTOF and other ECAL layers. 
     *
     */
    public List<DetectorParticle> findNeutrals(int ecalLayer) {

        int[] otherEcalLayers;
        switch (ecalLayer) {
            case (1):
                otherEcalLayers=new int[]{4,7};
                break;
            case (4):
                otherEcalLayers=new int[]{1,7};
                break;
            case (7):
                otherEcalLayers=new int[]{1,4};
                break;
            default:
                throw new RuntimeException("Invalid ECAL Layer:  "+ecalLayer);
        }

        List<DetectorParticle> parts=new ArrayList<DetectorParticle>();

        List<DetectorResponse> responsesECAL =
            eventBuilder.getUnmatchedResponses(null, DetectorType.ECAL, ecalLayer);

        for (DetectorResponse r : responsesECAL)
            parts.add(DetectorParticle.createNeutral(r));
        
        // add other responses:
        this.addResponsesECAL(parts,otherEcalLayers);
        this.addResponsesFTOF(parts);

        return parts;
    }

    /**
     *
     * Central Tracks and CTOF/CND were already matched before
     * event builder.  Copy in.
     *
     */
    /*
    public void readCentralParticles(DataEvent de,
                                     String ctrkBankName,
                                     String ctofBankName,
                                     String cndBankName) {

        // Make a neutral particle for each CND hit without an
        // associated track.
        if (de.hasBank(cndBankName)==true) {
            DataBank cndBank = de.getBank(cndBankName);
            final int ncnd=cndBank.rows();
            for (int icnd=0; icnd<ncnd; icnd++) {
                final int trkid=cndBank.getInt("trkID",icnd);
                if (trkid<1) {
                    // make neutral particle
                }
            }
        }

        // load map from trkID to ctof hits
        Map<Integer,ArrayList<Integer>> ctofMap = null;
        if (de.hasBank(ctofBankName)==true) {
            ctofMap = new HashMap<Integer,ArrayList<Integer>>();
            DataBank ctofBank = de.getBank(ctofBankName);
            final int nctof=ctofBank.rows();
            for (int ictof=0; ictof<nctof; ictof++) {
                final int trkid=ctofBank.getInt("trkID",ictof);
                if (trkid>0) {
                    if (!ctofMap.containsKey(trkid))
                        ctofMap.put(trkid,new ArrayList<Integer>());
                    ctofMap.get(trkid).add(ictof);
                }
            }
        }

        List<DetectorTracks> trks = new ArrayList<DetectorTrack>();
        List<DetectorParticle> prts = new ArrayList<DetectorParticle>();

        // Make a charged particle for each Central Track,
        // associate it with CTOF hit if found matching track ID.
        if (de.hasBank(ctrkBankName)==true) {
            DataBank ctrkBank = de.getBank(ctrkBankName);
            final int nctrk=ctrkBank.rows();
            for (int ictrk=0; ictrk<nctrk; ictrk++) {
                // make track and charged particle
                
                final int trkid=ctrkBank.getInt("ID",ictrk);
                if (ctofMap!=null && ctofMap.containsKey(trkid)) {
                    // associate the particle with a CTOF hit:
                
                }
            }
        }

        eventbuilder.addCentralTracks(trks);
    }
    */

}

