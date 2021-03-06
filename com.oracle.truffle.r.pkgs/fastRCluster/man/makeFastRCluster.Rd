% Generated by roxygen2: do not edit by hand
% Please edit documentation in R/fastRCluster.R
\name{makeFastRCluster}
\alias{makeFastRCluster}
\title{Creates cluster nodes that delegate to FastR}
\usage{
makeFastRCluster(nnodes = 1L, graalVMHome = getGraalVMHome(),
  mode = c("jvm", "native"), polyglot = FALSE, fastROptions = NULL,
  ...)
}
\arguments{
\item{nnodes}{Number of nodes to be created.}

\item{graalVMHome}{Path to the installation directory of GraalVM and FastR. Default value is obtained from \code{getGraalVMHome()}.}

\item{mode}{The mode in which to run FastR. See the FastR documentation for the details on the difference between jvm and native modes.}

\item{polyglot}{Run FastR in a polyglot mode: other installed GraalVM languages will be available via \code{eval.polyglot}. Allowed only for mode 'jvm' (the default).}

\item{fastROptions}{Additional options for the FastR engine.}

\item{...}{Additional options forwarded to \code{makePSOCKcluster}}
}
\value{
The cluster object that can be passed to functions like \code{parallel::clusterApply}.
}
\description{
FastR is an alternative implementation of the R programming language,
which provides superb performance for computation intensive and long
running jobs but takes bit more time to warm-up.
}
\examples{
\donttest{
fastrNode <- makeFastRCluster()
parallel::clusterApply(fastrNode, 'dummy', function(...) R.version)
fastr(fastrNode, R.version)
parallel::stopCluster(fastrNode)
}
}
\seealso{
\code{\link{getGraalVMHome}}
}
