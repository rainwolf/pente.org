$(document).ready(function()
{
    $("#statementBox").hover(

        function()
        {
            $("#stateDescription").html(statementDescription);
            $("#popUpDiv").show();
        },

        function()
        {
            $("#popUpDiv").hide();
        }
    );

    $("#disputeBox").hover(

        function()
        {
            $("#stateDescription").html(disputeDescription);
            $("#popUpDiv").show();
        },

        function()
        {
            $("#popUpDiv").hide();
        }
    );

    $("#secReviewRequestBox").hover(

        function()
        {
            $("#stateDescription").html(secReviewRequestDescription);
            $("#popUpDiv").show();
        },

        function()
        {
            $("#popUpDiv").hide();
        }
    );

    $("#supportDocsProcessedBox").hover(

        function()
        {
            $("#stateDescription").html(supportDocsProcessedDescription);
            $("#popUpDiv").show();
        },

        function()
        {
            $("#popUpDiv").hide();
        }
    );

    $("#caseUnderReviewBox").hover(

        function()
        {
            $("#stateDescription").html(caseUnderReviewDescription);
            $("#popUpDiv").show();
        },

        function()
        {
            $("#popUpDiv").hide();
        }
    );

    $("#decisionBox").hover(

        function()
        {
            $("#stateDescription").html(decisionDescription);
            $("#popUpDiv").show();
        },

        function()
        {
            $("#popUpDiv").hide();
        }
    );

    // Set the hidden text for the secretarial review images
    $("#statementDesc").append(statementDescriptionText);
    $("#disputeDesc").append(disputeDescriptionText);
    $("#secReviewRequestDesc").append(secReviewRequestDescriptionText);
    $("#supportDocsProcessedDesc").append(supportDocsProcessedDescriptionText);
    $("#caseUnderReviewDesc").append(caseUnderReviewDescriptionText);
    $("#decisionDesc").append(decisionDescriptionText);
});

// Set the descriptions for the various phases in the Secretarial Review process
var statementDescriptionText = "If you submit a subject statement, it becomes part of the report. The entity that submitted the Report will receive a copy of the Report including your statement. Additionally, any entity that queries, or has queried you in the past three years will receive the statement as part of the report.";
var statementDescription = "<div class=\"descriptionTitle\">Statement (Optional)</div><div>" + statementDescriptionText + "</div>";

var disputeDescriptionText = "If you dispute this Report, it will be noted in the report.  The entity that submitted the Report will receive a copy of the Report including your Dispute. Additionally, any entity that queries, or has queried you in the past three years will receive the Dispute as part of the report.";
var disputeDescription = "<div class=\"descriptionTitle\">Dispute</div><div>" + disputeDescriptionText + "</div>";

var secReviewRequestDescriptionText = "If you request Report Review of your disputed report, it will be noted in the report.  In order for the review process to begin you must send supporting documentation, as directed, at the time of your request. Once your supporting documentation is received, your request will be elevated by the Division of Practitioner Data Banks.";
var secReviewRequestDescription = "<div class=\"descriptionTitle\">Report Review Request</div><div>" + secReviewRequestDescriptionText + "</div>";

var supportDocsProcessedDescriptionText = "Completion of this phase means that the Data Bank received your documentation.";
var supportDocsProcessedDescription = "<div class=\"descriptionTitle\">Supporting Documents Processed</div><div>" + supportDocsProcessedDescriptionText + "</div>";

var caseUnderReviewDescriptionText = "Your request for Report Review to the Division of Practitioner Data Banks, along with the supporting documentation that you provided will be assigned to a Dispute Resolution Manager. Requests for Report Review are processed in the order that they are received. During this time, you or the reporting organization may be asked to provide additional information.";
var caseUnderReviewDescription = "<div class=\"descriptionTitle\">Case Under Review</div><div>" + caseUnderReviewDescriptionText + "</div>";

var decisionDescriptionText = "The Division of Practitioner Data Banks notifies you of the final decision. Details of this Decision will also be noted in the report.  The entity that submitted the Report will receive a copy of the Report including the Decision. Additionally, any entity that queries, or has queried you in the past three years will receive the Decision as part of the report.";
var decisionDescription = "<div class=\"descriptionTitle\">Decision</div><div>" + decisionDescriptionText + "</div>";
