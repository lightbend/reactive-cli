// Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
// No information contained herein may be reproduced or transmitted in any form
// or by any means without the express written permission of Lightbend, Inc.

// This is to add copyright headers to the build files

inConfig(Compile)(compileInputs.in(compile) := compileInputs.in(compile).dependsOn(createHeaders.in(compile)).value)

import de.heikoseeberger.sbtheader.HeaderPattern

headers := Map(
  "scala" -> (
    HeaderPattern.cStyleBlockComment,
    """|/*
      | * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
      | * No information contained herein may be reproduced or transmitted in any form
      | * or by any means without the express written permission of Lightbend, Inc.
      | */
      |
      |""".stripMargin
    )
)
