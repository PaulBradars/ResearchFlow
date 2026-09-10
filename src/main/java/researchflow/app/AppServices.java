package researchflow.app;

import researchflow.persistence.ConnectionFactory;
import researchflow.persistence.JdbcStudyRepository;
import researchflow.persistence.JdbcFormRepository;
import researchflow.persistence.JdbcResponseRepository;
import researchflow.persistence.JdbcDatasetRepository;
import researchflow.persistence.JdbcQualityRepository;
import researchflow.persistence.JdbcVersionRepository;
import researchflow.persistence.JdbcAnalysisRepository;
import researchflow.persistence.JdbcChatRepository;
import researchflow.persistence.JdbcFindingRepository;
import researchflow.persistence.MigrationRunner;
import researchflow.persistence.Seeder;
import researchflow.persistence.TransactionManager;
import researchflow.ai.DisabledLlmClient;
import researchflow.ai.LlmClient;
import researchflow.ai.LocalLlmClient;
import researchflow.service.StudyService;
import researchflow.service.FormService;
import researchflow.service.ResponseQueryService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.DatasetService;
import researchflow.service.DatasetCorrectionService;
import researchflow.service.AuditService;
import researchflow.service.QualityService;
import researchflow.service.QualityReviewService;
import researchflow.service.VersionService;
import researchflow.service.AnalysisService;
import researchflow.service.AnalysisFacade;
import researchflow.service.DatasetImportService;
import researchflow.service.FindingService;
import researchflow.service.ReportService;

import java.time.Duration;

public final class AppServices {
    private researchflow.collection.CollectionServer collection;

    public synchronized String collectionLink(java.util.UUID formId) {
        if (collection == null) {
            var port = System.getProperty("researchflow.collection.port",
                    System.getenv().getOrDefault("RESEARCHFLOW_COLLECTION_PORT", "8080"));
            var url = System.getProperty("researchflow.collection.publicUrl",
                    System.getenv().getOrDefault("RESEARCHFLOW_COLLECTION_PUBLIC_URL", ""));
            try {
                collection = new researchflow.collection.CollectionServer(forms, submissions, connections,
                        new java.net.InetSocketAddress("0.0.0.0", Integer.parseInt(port)), url);
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Could not start collection on port " + port + ". Check whether the port is already in use.", failure);
            }
        }
        return collection.link(formId);
    }

    public synchronized void stopCollection() {
        if (collection != null) { collection.close(); collection = null; }
    }
    private final AppConfig config;
    private final ConnectionFactory connections;
    private final StudyService studies;
    private final FormService forms;
    private final ResponseSubmissionService submissions;
    private final ResponseQueryService responses;
    private final DatasetService datasets;
    private final DatasetCorrectionService corrections;
    private final AuditService audits;
    private final QualityService quality;
    private final QualityReviewService qualityReview;
    private final VersionService versions;
    private final AnalysisService analysis;
    private final AnalysisFacade aiFacade;
    private final DatasetImportService imports;
    private final FindingService findings;
    private final ReportService reports;

    private AppServices(AppConfig config, ConnectionFactory connections, StudyService studies,
                        FormService forms, ResponseSubmissionService submissions, ResponseQueryService responses,
                        DatasetService datasets, DatasetCorrectionService corrections, AuditService audits,
                        QualityService quality, QualityReviewService qualityReview, VersionService versions,
                        AnalysisService analysis, AnalysisFacade aiFacade, DatasetImportService imports,
                        FindingService findings, ReportService reports) {
        this.config = config;
        this.connections = connections;
        this.studies = studies;
        this.forms = forms;
        this.submissions = submissions;
        this.responses = responses;
        this.datasets = datasets;
        this.corrections = corrections;
        this.audits = audits;
        this.quality = quality;
        this.qualityReview = qualityReview;
        this.versions = versions;
        this.analysis = analysis;
        this.aiFacade = aiFacade;
        this.imports = imports;
        this.findings = findings;
        this.reports = reports;
    }

    public static AppServices initialize(AppConfig config) {
        var connections = new ConnectionFactory(config.databasePath());
        new MigrationRunner(connections).migrate();
        var transactions = new TransactionManager(connections);
        var repository = new JdbcStudyRepository(connections, transactions);
        var studies = new StudyService(repository);
        var writeGuard = new researchflow.service.StudyWriteGuard(repository);
        var formRepository = new JdbcFormRepository(connections, transactions);
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var forms = new FormService(formRepository, writeGuard);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository, writeGuard);
        var responses = new ResponseQueryService(responseRepository);
        var datasetRepository = new JdbcDatasetRepository(connections, transactions);
        var datasets = new DatasetService(datasetRepository, formRepository);
        var corrections = new DatasetCorrectionService(datasetRepository, formRepository, writeGuard);
        var audits = new AuditService(datasetRepository, writeGuard);
        var qualityRepository = new JdbcQualityRepository(connections, transactions);
        var quality = new QualityService(formRepository, responseRepository, qualityRepository, writeGuard);
        var qualityReview = new QualityReviewService(qualityRepository, corrections, writeGuard);
        var versionRepository = new JdbcVersionRepository(connections, transactions);
        var versions = new VersionService(versionRepository, writeGuard);
        var analysisRepository = new JdbcAnalysisRepository(connections, transactions);
        var analysis = new AnalysisService(formRepository, versionRepository, analysisRepository, writeGuard);
        LlmClient llmClient = config.aiEnabled()
                ? new LocalLlmClient(config.aiBaseUrl(), config.aiModel(), Duration.ofSeconds(config.aiTimeoutSeconds()))
                : new DisabledLlmClient();
        var chatRepository = new JdbcChatRepository(connections);
        var aiFacade = new AnalysisFacade(llmClient, formRepository, analysis, chatRepository);
        var imports = new DatasetImportService(forms, submissions);
        var findingRepository = new JdbcFindingRepository(connections, transactions);
        var findings = new FindingService(findingRepository, writeGuard);
        var reports = new ReportService(studies, forms, responses, quality, versions, findings, audits, analysis);
        if (config.seedDevelopmentData()) {
            new Seeder(studies, forms, submissions).seedIfEmpty();
        }
        return new AppServices(config, connections, studies, forms, submissions, responses,
                datasets, corrections, audits, quality, qualityReview, versions, analysis, aiFacade, imports,
                findings, reports);
    }

    public researchflow.service.DatabaseRecoveryService recovery() {
        return new researchflow.service.DatabaseRecoveryService(connections, config.databasePath());
    }

    public AppConfig config() {
        return config;
    }

    public ConnectionFactory connections() {
        return connections;
    }

    public StudyService studies() {
        return studies;
    }

    public FormService forms() { return forms; }
    public ResponseSubmissionService submissions() { return submissions; }
    public ResponseQueryService responses() { return responses; }
    public DatasetService datasets() { return datasets; }
    public DatasetCorrectionService corrections() { return corrections; }
    public AuditService audits() { return audits; }
    public QualityService quality() { return quality; }
    public QualityReviewService qualityReview() { return qualityReview; }
    public VersionService versions() { return versions; }
    public AnalysisService analysis() { return analysis; }
    public AnalysisFacade aiFacade() { return aiFacade; }
    public DatasetImportService imports() { return imports; }
    public FindingService findings() { return findings; }
    public ReportService reports() { return reports; }
}
