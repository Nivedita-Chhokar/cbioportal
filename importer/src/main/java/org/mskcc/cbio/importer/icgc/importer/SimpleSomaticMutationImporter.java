/*
 *  Copyright (c) 2014 Memorial Sloan-Kettering Cancer Center.
 * 
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 *  MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 *  documentation provided hereunder is on an "as is" basis, and
 *  Memorial Sloan-Kettering Cancer Center 
 *  has no obligations to provide maintenance, support,
 *  updates, enhancements or modifications.  In no event shall
 *  Memorial Sloan-Kettering Cancer Center
 *  be liable to any party for direct, indirect, special,
 *  incidental or consequential damages, including lost profits, arising
 *  out of the use of this software and its documentation, even if
 *  Memorial Sloan-Kettering Cancer Center 
 *  has been advised of the possibility of such damage.
 */
package org.mskcc.cbio.importer.icgc.importer;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.*;
import org.apache.log4j.Logger;
import org.mskcc.cbio.importer.icgc.etl.IcgcStudyEtlCallable;
import org.mskcc.cbio.importer.icgc.support.IcgcImportService;
import org.mskcc.cbio.importer.icgc.support.IcgcMetadataService;
import org.mskcc.cbio.importer.icgc.transformer.IcgcFileTransformer;
import org.mskcc.cbio.importer.icgc.transformer.SimpleSomaticFileTransformer;
import org.mskcc.cbio.importer.model.IcgcMetadata;
import org.mskcc.cbio.importer.persistence.staging.StagingCommonNames;
import org.mskcc.cbio.importer.persistence.staging.mutation.MutationFileHandlerImpl;
import org.mskcc.cbio.importer.util.PropertiesLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import scala.Tuple3;

import javax.annotation.Nullable;

/*
 responsible for invoking ETL operations for simple somatic ICGC studys
 primary input is a list of ICGC studys

 */
public class SimpleSomaticMutationImporter implements Callable<List<String>> {

    /*
    responsible for:
        1. generating a List of URLs for ICGC simple somatic mutation files
        2. instantiating a SimpleSomaticFileTransformer object
        3. invoking multiple IcgcStudyFileETL operations to import ICGC data

     */

    private static Logger logger = Logger.getLogger(SimpleSomaticMutationImporter.class);
    private static final Integer ETL_THREADS = 4;
    private final Path baseStagingPath;
    final ListeningExecutorService service =
            MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(ETL_THREADS));


    public SimpleSomaticMutationImporter( ) {
        this.baseStagingPath = PropertiesLoader.getInstance().getImporterBasePath();
    }

    //for testing purposes only - limit access
     SimpleSomaticMutationImporter(Path aPath ) {
        Preconditions.checkArgument(null != aPath);
        this.baseStagingPath = aPath;
    }

    @Override
    public List<String> call() throws Exception {
        return this.processSimpleSomaticMutations();
    }
    /*
    private method to create a Collection of the attributes needed to import & transform
    ICGC files
    use a Tuple3 as a data value object containing: (1) Path to the study's staging file directory,
    (2) the URL to the ICGC source file, and (3) a ICGCFileTransformer implementation
     */
    private List<Tuple3<Path,String,IcgcFileTransformer>> resolveImportAttributeList(){
        // get simple somatic mutation URLs for registered studies
        final Map<String, String> urlMap = IcgcImportService.INSTANCE.getIcgcMutationUrlMap();
        return FluentIterable.from(urlMap.keySet())
                .transform(new Function<String,Tuple3<Path,String,IcgcFileTransformer>>() {
                    @Nullable
                    @Override
                    public Tuple3<Path, String, IcgcFileTransformer> apply(String studyId) {
                        final IcgcMetadata meta = IcgcMetadataService.INSTANCE.getIcgcMetadataById(studyId);
                        final Path stagingDirectoryPath = Paths.get(StagingCommonNames.pathJoiner.join(baseStagingPath,
                               meta.getDownloaddirectory()) );
                        final String url = urlMap.get(studyId);
                        final IcgcFileTransformer  transformer = (IcgcFileTransformer) new SimpleSomaticFileTransformer(
                                new MutationFileHandlerImpl(),stagingDirectoryPath);
                        return new Tuple3<Path, String, IcgcFileTransformer>(stagingDirectoryPath, url,
                                transformer);
                    }
                }).toList();
    }

    /*
    public method to initiate processing of simple somatic mutations for ICGC studies registered
    in the cbio portal
    Returns a List of Strings (i.e. messages) for successfully processed studies
     */
    public  List<String> processSimpleSomaticMutations() {
        final List<String> retList = Lists.newArrayList();
        List<ListenableFuture<String>> futureList = Lists.newArrayList();
     for (Tuple3<Path, String, IcgcFileTransformer> tuple3 : this.resolveImportAttributeList()){
         futureList.add( service.submit(new IcgcStudyEtlCallable(tuple3._1(),
                 tuple3._2(), tuple3._3())));
         ListenableFuture<List<String>> etlResults = Futures.successfulAsList(futureList);
         Futures.addCallback(etlResults, new FutureCallback<List<String>>() {
             @Override
             public void onSuccess(List<String> resultList) {
                 for (String r : resultList) {
                     logger.info(r);
                     retList.add(r);
                 }
             }
             @Override
             public void onFailure(Throwable t) {
                 logger.error(t.getMessage());
             }
         });

     }
       return retList;
    }

    /*
    main method for testing
    */
    public static void main(String...args){
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("/applicationContext-importer.xml");
        SimpleSomaticMutationImporter importer = (SimpleSomaticMutationImporter) applicationContext.getBean("icgcSimpleSomaticImporter");
        for(String result : importer.processSimpleSomaticMutations()){
            logger.info("++++RESULT: " +result);
        }

      logger.info("Finis");
        
    }

}
