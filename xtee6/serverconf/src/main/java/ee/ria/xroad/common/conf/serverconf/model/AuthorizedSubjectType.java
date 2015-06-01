package ee.ria.xroad.common.conf.serverconf.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

import ee.ria.xroad.common.identifier.XroadId;

/**
 * Authorized subject.
 */
@Getter
@Setter
public class AuthorizedSubjectType {

    private Long id;

    private XroadId subjectId;

    private Date rightsGiven;
}