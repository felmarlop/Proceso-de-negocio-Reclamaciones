package es.us.isa;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import org.joda.time.DateTimeConstants;
import org.joda.time.LocalTime;

import es.us.isa.aml.AgreementManager;
import es.us.isa.aml.model.AgreementOffer;
import es.us.isa.ppinot.evaluation.Aggregator;
import es.us.isa.ppinot.evaluation.LogMeasureEvaluator;
import es.us.isa.ppinot.evaluation.Measure;
import es.us.isa.ppinot.evaluation.MeasureEvaluator;
import es.us.isa.ppinot.evaluation.logs.LogProvider;
import es.us.isa.ppinot.evaluation.logs.MXMLLog;
import es.us.isa.ppinot.model.MeasureDefinition;
import es.us.isa.ppinot.model.Schedule;
import es.us.isa.ppinot.model.TimeUnit;
import es.us.isa.ppinot.model.aggregated.AggregatedMeasure;
import es.us.isa.ppinot.model.base.CountMeasure;
import es.us.isa.ppinot.model.base.TimeMeasure;
import es.us.isa.ppinot.model.condition.TimeInstantCondition;
import es.us.isa.ppinot.model.condition.TimeMeasureType;
import es.us.isa.ppinot.model.derived.DerivedMultiInstanceMeasure;
import es.us.isa.ppinot.model.derived.DerivedSingleInstanceMeasure;
import es.us.isa.ppinot.model.scope.Period;
import es.us.isa.ppinot.model.scope.SimpleTimeFilter;
import es.us.isa.ppinot.model.state.GenericState;

public class AppReclamaciones {

	public static void main(String[] args) throws Exception {
		AppReclamaciones appReclamaciones = new AppReclamaciones();
		appReclamaciones.evaluateAgreement();
	}
	
	public void evaluateAgreement() throws Exception {
		AgreementManager am = new AgreementManager();
		am.getStoreManager().registerFromFolder(new File("templates").getAbsolutePath(),false);
		
		String templateName = "bpaas";
		String clientId = "basicSimulator";
		am.getStoreManager().getAgreementTemplate(templateName).generateAgreementOffer(clientId).generateAgreement(clientId).register(clientId);
		AgreementOffer ao = am.getStoreManager().getAgreementOffer(clientId);

		List<Measure> measures = compute(buildTCumplidos());
		printMeasures(measures);
		for (Measure m: measures) {
			ao.setProperty("TCumplidos", m.getValue());			
			System.out.println(ao.evaluateGT("G1")); 
		}
	}
	
	private List<Measure> compute(MeasureDefinition measure) throws Exception {
		LogProvider mxmlLog = new MXMLLog(new FileInputStream(new File("logs/simulation_logs (5000).mxml")), null);
		MeasureEvaluator evaluator = new LogMeasureEvaluator(mxmlLog);		

		return evaluator.eval(measure, new SimpleTimeFilter(Period.MONTHLY, 1, false));
	}

	private MeasureDefinition buildTCumplidos() throws Exception {
		Schedule workingHours = new Schedule(DateTimeConstants.MONDAY, DateTimeConstants.FRIDAY, new LocalTime(9,0), new LocalTime(17,0));
		TimeMeasure tiempoProceso = new TimeMeasure();
		tiempoProceso.setFrom(new TimeInstantCondition("Reclamacion recibida", GenericState.START));
		tiempoProceso.setTo(new TimeInstantCondition("Registrar informacion", GenericState.END));
		tiempoProceso.setConsiderOnly(workingHours);
        tiempoProceso.setUnitOfMeasure(TimeUnit.HOURS);			
		
		TimeMeasure tiempoEvaluacion = new TimeMeasure();
		tiempoEvaluacion.setFrom(new TimeInstantCondition("Evaluar reclamacion", GenericState.START));
		tiempoEvaluacion.setTo(new TimeInstantCondition("Evaluar reclamacion", GenericState.END));
		tiempoEvaluacion.setConsiderOnly(workingHours);
		tiempoEvaluacion.setUnitOfMeasure(TimeUnit.HOURS);		
		
		TimeMeasure tiempoRevision = new TimeMeasure();
		tiempoRevision.setFrom(new TimeInstantCondition("Revisar formularios", GenericState.START));
		tiempoRevision.setTo(new TimeInstantCondition("Revisar formularios", GenericState.END));
		tiempoRevision.setTimeMeasureType(TimeMeasureType.CYCLIC);
		tiempoRevision.setSingleInstanceAggFunction(Aggregator.SUM);
		tiempoRevision.setConsiderOnly(workingHours);
		tiempoRevision.setUnitOfMeasure(TimeUnit.HOURS);	
		
		CountMeasure isCritical = new CountMeasure();
		isCritical.setWhen(new TimeInstantCondition("Critico", GenericState.START));
		CountMeasure isHigh = new CountMeasure();
		isHigh.setWhen(new TimeInstantCondition("Alto", GenericState.START));
		CountMeasure isLow = new CountMeasure();
		isLow.setWhen(new TimeInstantCondition("Bajo", GenericState.START));
		
		DerivedSingleInstanceMeasure accomplishedIntervention = new DerivedSingleInstanceMeasure();
		accomplishedIntervention.setFunction("( !(isCritical > 0) || ((tproc < 1 || Double.isNaN(tproc)) && (teval < 0.25 || Double.isNaN(teval)) && (trev < 0.25 || Double.isNaN(trev)))) &&"
				+"( !(isHigh > 0) || ((tproc < 2 || Double.isNaN(tproc)) && (teval < 0.5 || Double.isNaN(teval)) && (trev < 0.5 || Double.isNaN(trev)))) &&"
				+ "( !(isLow > 0) || ((tproc < 4 || Double.isNaN(tproc)) && (teval < 1 || Double.isNaN(teval)) && (trev < 1 || Double.isNaN(trev))))");
		accomplishedIntervention.addUsedMeasure("isCritical", isCritical);
		accomplishedIntervention.addUsedMeasure("isHigh", isHigh);
		accomplishedIntervention.addUsedMeasure("isLow", isLow);
		accomplishedIntervention.addUsedMeasure("tproc", tiempoProceso);
		accomplishedIntervention.addUsedMeasure("teval", tiempoEvaluacion);
		accomplishedIntervention.addUsedMeasure("trev", tiempoRevision);
		
		AggregatedMeasure totalAccomplished = new AggregatedMeasure();
		totalAccomplished.setBaseMeasure(accomplishedIntervention);
		totalAccomplished.setAggregationFunction(Aggregator.SUM);
		
		AggregatedMeasure totalInterventions = new AggregatedMeasure();
		CountMeasure count = new CountMeasure();
		count.setWhen(new TimeInstantCondition("Reclamacion recibida", GenericState.START));
		totalInterventions.setBaseMeasure(count);
		totalInterventions.setAggregationFunction(Aggregator.SUM);
		
		DerivedMultiInstanceMeasure tCumplidos = new DerivedMultiInstanceMeasure();
		tCumplidos.setFunction("a/b*100");
		tCumplidos.addUsedMeasure("a", totalAccomplished);
		tCumplidos.addUsedMeasure("b", totalInterventions);

		return tCumplidos;		
	}
	
	private void printMeasures(List<Measure> measures) {
		for (Measure m: measures) {
        	System.out.println("Value: " + m.getValue());
        	System.out.println("Number of instances: " + m.getInstances().size());
        	System.out.println("Instances: " + m.getInstances());
        	System.out.println("--");
		}
	}

}
